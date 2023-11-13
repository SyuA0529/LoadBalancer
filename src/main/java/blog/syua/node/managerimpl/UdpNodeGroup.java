package blog.syua.node.managerimpl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blog.syua.node.Node;
import blog.syua.node.NodeGroup;
import blog.syua.node.nodeimpl.UdpNode;
import blog.syua.utils.ThreadPoolUtils;

public class UdpNodeGroup implements NodeGroup {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final Queue<UdpNode> udpNodes;
	private final ExecutorService threadPool;
	private final DatagramSocket listenSocket;
	private boolean isAvailable;

	public UdpNodeGroup(int port) throws SocketException {
		udpNodes = new LinkedList<>();
		listenSocket = new DatagramSocket(port);
		listenSocket.setSoTimeout(UdpNode.TIME_OUT);
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
				logger.info("UdpNodeManager: Socket Time Out\n{} {}", clientPacket.getAddress(), clientPacket.getPort());
			} catch (IOException e) {
				logger.error("UdpNodeManager: Error occur in startForward\n{}", Arrays.toString(e.getStackTrace()));
				throw new IllegalThreadStateException("패킷을 받을 수 없습니다");
			}
		}).start();
	}

	@Override
	public Collection<Node> getNodes() {
		return new ArrayList<>(udpNodes);
	}

	@Override
	public synchronized void registerNode(Node udpNode) {
		if (!(udpNode instanceof UdpNode)) {
			throw new IllegalArgumentException("UDP 노드가 아닙니다");
		}
		udpNodes.offer((UdpNode)udpNode);
		logger.info("UdpNodeManager: registerNode {}", udpNode);
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
		logger.info("UdpNodeManager: unregisterNode {}", udpNode);
	}

	private synchronized UdpNode selectNode() {
		UdpNode curNode = udpNodes.poll();
		udpNodes.offer(curNode);
		return curNode;
	}

}
