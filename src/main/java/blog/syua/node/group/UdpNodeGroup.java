package blog.syua.node.group;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;

import blog.syua.node.node.Node;
import blog.syua.node.node.Protocol;
import blog.syua.node.node.UdpNode;
import blog.syua.utils.NodeMessageUtil;
import blog.syua.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpNodeGroup implements NodeGroup {

	@Value("${loadbalancer.tcp.thread-pool-size:4}")
	private final int threadPoolSize = Runtime.getRuntime().availableProcessors();

	@Value("${loadbalancer.udp.timeout:5000}")
	public final int timeout = 5000;
	private final Queue<UdpNode> udpNodes;
	private final ExecutorService threadPool;
	private final DatagramSocket listenSocket;
	private boolean isRunning;

	public UdpNodeGroup(int port) throws SocketException {
		udpNodes = new LinkedList<>();
		listenSocket = new DatagramSocket(port);
		listenSocket.setSoTimeout(timeout);
		isRunning = false;
		threadPool = Executors.newFixedThreadPool(threadPoolSize);
	}

	@Override
	public void startForwarding() {
		if (udpNodes.isEmpty()) {
			throw new IllegalStateException("Unable to start forwarding");
		}
		if (isRunning) {
			throw new IllegalStateException("Forwarding is already in progress");
		}
		isRunning = true;
		new Thread(() -> {
			DatagramPacket clientPacket = null;
			try {
				while (isRunning) {
					clientPacket = new DatagramPacket(new byte[Protocol.UDP.getMaxReceiveSize()],
						Protocol.UDP.getMaxReceiveSize());
					listenSocket.receive(clientPacket);
					DatagramPacket forwardClientPacket = clientPacket;
					threadPool.execute(() -> selectNode().forwardPacket(listenSocket, forwardClientPacket));
				}
			} catch (SocketTimeoutException timeoutException) {
				log.info("Socket Time Out - {Ip: {}, Port: {}}", clientPacket.getAddress(),
					clientPacket.getPort());
			} catch (Exception exception) {
				checkSocketException(exception);
			}
		}).start();
	}

	@Override
	public synchronized void registerNode(Node udpNode) {
		if (!(udpNode instanceof UdpNode)) {
			throw new IllegalArgumentException("Not UDP node");
		}
		udpNodes.offer((UdpNode)udpNode);
		log.info("RegisterNode - {}", udpNode);
	}

	@Override
	public void unRegisterNode(Node udpNode) {
		if (!(udpNode instanceof UdpNode)) {
			throw new IllegalArgumentException("Not UDP node");
		}
		udpNodes.remove(udpNode);
		if (udpNodes.isEmpty()) {
			isRunning = false;
			ThreadPoolUtils.removeThreadPool(threadPool, listenSocket);
		}
		log.info("UnRegisterNode - {}", udpNode);
	}

	@Override
	public boolean isEmpty() {
		return udpNodes.isEmpty();
	}

	private void checkSocketException(Exception exception) {
		if (exception instanceof SocketException &&
			exception.getMessage().equals(NodeMessageUtil.getSocketInterruptMessage(Protocol.UDP))) {
			log.info("Stop Forward - {}", this);
		}
		log.error("Unable to forward packets");
		exception.printStackTrace();
		Thread.currentThread().interrupt();
	}

	private synchronized UdpNode selectNode() {
		UdpNode curNode = udpNodes.poll();
		udpNodes.offer(curNode);
		return curNode;
	}

	@Override
	public String toString() {
		return "UdpNodeGroup{" +
			"protocol=" + Protocol.UDP +
			", port=" + listenSocket.getLocalPort() +
			'}';
	}

}
