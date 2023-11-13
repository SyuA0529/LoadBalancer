package blog.syua.node.group;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
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

	@Value("${loadbalancer.udp.timeout}")
	public final int timeout = 3000;
	private final Queue<UdpNode> udpNodes;
	private final ExecutorService threadPool;
	private final DatagramSocket listenSocket;
	private boolean isAvailable;

	public UdpNodeGroup(int port) throws SocketException {
		udpNodes = new LinkedList<>();
		listenSocket = new DatagramSocket(port);
		listenSocket.setSoTimeout(timeout);
		isAvailable = false;
		threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	@Override
	public void startForwarding() {
		if (udpNodes.isEmpty()) {
			throw new IllegalStateException("포워딩을 시작할 수 없습니다");
		}
		if (isAvailable) {
			throw new IllegalStateException("이미 포워딩이 진행중입니다");
		}
		isAvailable = true;
		new Thread(() -> {
			DatagramPacket clientPacket = null;
			try {
				while (isAvailable) {
					clientPacket = new DatagramPacket(new byte[listenSocket.getReceiveBufferSize()],
						listenSocket.getReceiveBufferSize());
					listenSocket.receive(clientPacket);
					DatagramPacket forwardClientPacket = clientPacket;
					threadPool.execute(() -> selectNode().forwardPacket(listenSocket, forwardClientPacket));
				}
			} catch (SocketTimeoutException timeoutException) {
				log.info("UdpNodeGroup: Socket Time Out - {Ip: {}, Port: {}}", clientPacket.getAddress(), clientPacket.getPort());
			} catch (IOException exception) {
				checkSocketException(exception);
			}
		}).start();
	}

	@Override
	public synchronized void registerNode(Node udpNode) {
		if (!(udpNode instanceof UdpNode)) {
			throw new IllegalArgumentException("UDP 노드가 아닙니다");
		}
		udpNodes.offer((UdpNode)udpNode);
		log.info("UdpNodeGroup: registerNode - {}", udpNode);
	}

	@Override
	public void unRegisterNode(Node udpNode) {
		if (!(udpNode instanceof UdpNode)) {
			throw new IllegalArgumentException("UDP 노드가 아닙니다");
		}
		udpNodes.remove(udpNode);
		if (udpNodes.isEmpty()) {
			isAvailable = false;
			ThreadPoolUtils.removeThreadPool(threadPool, listenSocket);
		}
		log.info("UdpNodeGroup: unregisterNode - {}", udpNode);
	}

	@Override
	public boolean isEmpty() {
		return udpNodes.isEmpty();
	}

	private void checkSocketException(IOException exception) {
		if (exception instanceof SocketException &&
			exception.getMessage().equals(NodeMessageUtil.getSocketInterruptMessage(Protocol.UDP))) {
			log.info("UdpNodeGroup: Stop Forward - {}", this);
			return;
		}
		log.error("UdpNodeGroup: Error occur in startForward\n{}", Arrays.toString(exception.getStackTrace()));
		throw new IllegalThreadStateException("패킷을 받을 수 없습니다" + exception.getMessage());
	}

	private synchronized UdpNode selectNode() {
		UdpNode curNode = udpNodes.poll();
		udpNodes.offer(curNode);
		return curNode;
	}

}
