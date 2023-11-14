package blog.syua.healthcheck;

import java.io.IOException;
import java.util.Arrays;
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
			throw new IllegalArgumentException("이미 존재하는 노드입니다");
		}
		ScheduledFuture<?> scheduledFuture = scheduledThreadPool.scheduleWithFixedDelay(() -> checkNodeHealthy(node),
			healthCheckDelay, healthCheckDelay, TimeUnit.MILLISECONDS);
		scheduledFutures.put(node, scheduledFuture);
	}

	@Override
	public void onUnRegisterNode(Node node) {
		if (scheduledFutures.containsKey(node)) {
			removeScheduledHealthCheckTask(node);
		}
	}

	private void checkNodeHealthy(Node node) {
		log.info("HealthChecker: Start Task of Node-{} Health Check", node);
		if (!node.isHealthy()) {
			log.info("HealthChecker: Remove Task of Node-{} Health Check", node);
			removeScheduledHealthCheckTask(node);
			unRegisterUnHealthyNode(node);
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
			throw new IllegalStateException("헬스 체크 대상인 노드의 작업을 찾을 수 없습니다");
		}
		return scheduledFuture;
	}

	private void unRegisterUnHealthyNode(Node node) {
		try {
			nodeGroupManager.unRegisterNode(node.getProtocol(), node.getIpAddr(), node.getPort());
		} catch (IOException exception) {
			log.error("HealthCheckerImpl: Error occur in UnRegister Node\n{}",
				Arrays.toString(exception.getStackTrace()));
		}
	}

}
