package blog.syua.node.group;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;

import blog.syua.node.node.Node;
import blog.syua.node.node.Protocol;
import blog.syua.node.node.TcpNode;
import blog.syua.utils.NodeMessageUtil;
import blog.syua.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpNodeGroup implements NodeGroup {

	@Value("${loadbalancer.tcp.thread-pool-size:4}")
	private final int threadPoolSize = Runtime.getRuntime().availableProcessors();

	@Value("${loadbalancer.tcp.timeout:5000}")
	private final int tcpTimeOut = 5000;

	private final Queue<TcpNode> tcpNodes;
	private final ExecutorService threadPool;
	private final ServerSocket listenSocket;
	private boolean isRunning;

	public TcpNodeGroup(int port) throws IOException {
		tcpNodes = new LinkedList<>();
		listenSocket = new ServerSocket(port);
		listenSocket.setSoTimeout(tcpTimeOut);
		isRunning = false;
		threadPool = Executors.newFixedThreadPool(threadPoolSize);
	}

	@Override
	public void startForwarding() {
		if (tcpNodes.isEmpty()) {
			throw new IllegalStateException("Unable to start forwarding");
		}
		if (isRunning) {
			throw new IllegalStateException("Forwarding is already in progress");
		}
		isRunning = true;
		new Thread(() -> {
			log.info("StartForward - {}", this);
			Socket clientSocket;
			try {
				while (isRunning && Objects.nonNull(clientSocket = listenSocket.accept())) {
					log.info("Connect new client - {} {}", clientSocket.getInetAddress(),
						clientSocket.getPort());
					Socket finalClientSocket = clientSocket;
					threadPool.execute(() -> selectNode().forwardPacket(finalClientSocket));
				}
			} catch (Exception exception) {
				checkSocketException(exception);
			}
		}).start();

	}

	@Override
	public synchronized void registerNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("Not a TCP node");
		}
		tcpNodes.offer((TcpNode)tcpNode);
		log.info("RegisterNode - {}", tcpNode);
	}

	@Override
	public synchronized void unRegisterNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("Not a TCP node");
		}
		tcpNodes.remove(tcpNode);
		log.info("UnRegisterNode - {}", tcpNode);
		if (tcpNodes.isEmpty()) {
			isRunning = false;
			ThreadPoolUtils.removeThreadPool(threadPool, listenSocket);
		}
	}

	@Override
	public boolean isEmpty() {
		return tcpNodes.isEmpty();
	}

	private void checkSocketException(Exception exception) {
		if (exception instanceof SocketException &&
			exception.getMessage().equals(NodeMessageUtil.getSocketInterruptMessage(Protocol.TCP))) {
			log.info("Stop Forward - {}", this);
		}
		log.error("Unable to forward packets");
		exception.printStackTrace();
		Thread.currentThread().interrupt();
	}

	private synchronized TcpNode selectNode() {
		TcpNode curNode = tcpNodes.poll();
		tcpNodes.offer(curNode);
		return curNode;
	}

	@Override
	public String toString() {
		return "TcpNodeGroup{" +
			"protocol=" + Protocol.TCP +
			", port=" + listenSocket.getLocalPort() +
			'}';
	}

}
