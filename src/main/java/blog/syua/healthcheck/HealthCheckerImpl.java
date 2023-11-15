package blog.syua.healthcheck;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import blog.syua.node.groupmanager.NodeGroupManager;
import blog.syua.node.node.Node;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HealthCheckerImpl implements HealthChecker {

	private final NodeGroupManager nodeGroupManager;
	private final ScheduledExecutorService scheduledThreadPool;
	private final ConcurrentHashMap<Node, ScheduledFuture<?>> scheduledFutures;

	@Value("${loadbalancer.healthcheck.thread-num}")
	private final int healthCheckThreadNum = Runtime.getRuntime().availableProcessors();

	@Value("${loadbalancer.healthcheck.delay}")
	private final int healthCheckDelay = 3000;

	@Autowired
	public HealthCheckerImpl(NodeGroupManager nodeGroupManager) {
		this.nodeGroupManager = nodeGroupManager;
		nodeGroupManager.registerListener(this);
		scheduledThreadPool = Executors.newScheduledThreadPool(healthCheckThreadNum);
		scheduledFutures = new ConcurrentHashMap<>();
	}

	@Override
	public void onRegisterNode(Node node) {
		if (Objects.nonNull(scheduledFutures.get(node))) {
			log.info("Already undergoing health check - {}", node);
			throw new IllegalArgumentException("Node already exists");
		}
		ScheduledFuture<?> scheduledFuture = scheduledThreadPool.scheduleWithFixedDelay(() -> checkNodeHealthy(node),
			healthCheckDelay, healthCheckDelay, TimeUnit.MILLISECONDS);
		scheduledFutures.put(node, scheduledFuture);
	}

	@Override
	public void onUnRegisterNode(Node node) {
		if (scheduledFutures.containsKey(node)) {
			log.info("Remove Task of Node-{} Health Check", node);
			removeScheduledHealthCheckTask(node);
		}
	}

	private void checkNodeHealthy(Node node) {
		log.info("Start Task of Node-{} Health Check", node);
		try {
			if (!node.isHealthy()) {
				log.info("Remove Task of Node-{} Health Check", node);
				removeScheduledHealthCheckTask(node);
				unRegisterUnHealthyNode(node);
			}
		} catch (Exception exception) {
			log.error("Error occur in checkNodeHealthy - {}", node);
			exception.printStackTrace();
		}
	}

	private void removeScheduledHealthCheckTask(Node node) {
		ScheduledFuture<?> scheduledFuture = getScheduledHealthCheckTask(node);
		scheduledFuture.cancel(true);
		scheduledFutures.remove(node);
	}

	private ScheduledFuture<?> getScheduledHealthCheckTask(Node node) {
		ScheduledFuture<?> scheduledFuture = scheduledFutures.get(node);
		if (Objects.isNull(scheduledFuture)) {
			throw new IllegalStateException("No task found for the node to be health checked");
		}
		return scheduledFuture;
	}

	private void unRegisterUnHealthyNode(Node node) {
		try {
			nodeGroupManager.unRegisterNode(node.getProtocol(), node.getIpAddr(), node.getPort());
		} catch (Exception exception) {
			log.error("Error occur in UnRegister Node");
			exception.printStackTrace();
		}
	}

}
