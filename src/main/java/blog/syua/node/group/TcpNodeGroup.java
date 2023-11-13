package blog.syua.node.group;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
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

	@Value("${loadbalancer.tcp.thread-pool-size}")
	private final int threadPoolSize = Runtime.getRuntime().availableProcessors();

	private final Queue<TcpNode> tcpNodes;
	private final ExecutorService threadPool;
	private final ServerSocket listenSocket;
	private boolean isAvailable;

	public TcpNodeGroup(int port) throws IOException {
		tcpNodes = new LinkedList<>();
		listenSocket = new ServerSocket(port);
		isAvailable = false;
		threadPool = Executors.newFixedThreadPool(threadPoolSize);
	}

	@Override
	public void startForwarding() {
		if (tcpNodes.isEmpty()) {
			throw new IllegalStateException("포워딩을 시작할 수 없습니다");
		}
		if (isAvailable) {
			throw new IllegalStateException("이미 포워딩이 진행중입니다");
		}
		isAvailable = true;
		new Thread(() -> {
			log.info("TcpNodeGroup: StartForward - {}", this);
			Socket clientSocket;
			try {
				while (isAvailable && Objects.nonNull(clientSocket = listenSocket.accept())) {
					log.info("TcpNodeGroup: Connect new client - {} {}", clientSocket.getInetAddress(),
						clientSocket.getPort());
					Socket finalClientSocket = clientSocket;
					threadPool.execute(() -> selectNode().forwardPacket(finalClientSocket));
				}
			} catch (IOException exception) {
				checkSocketException(exception);
			}

		}).start();

	}

	@Override
	public synchronized void registerNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("TCP 노드가 아닙니다");
		}
		tcpNodes.offer((TcpNode)tcpNode);
		log.info("TcpNodeGroup: RegisterNode - {}", tcpNode);
	}

	@Override
	public synchronized void unRegisterNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("TCP 노드가 아닙니다");
		}
		tcpNodes.remove(tcpNode);
		tcpNode.closeConnection();
		log.info("TcpNodeGroup: UnregisterNode - {}", tcpNode);
		if (tcpNodes.isEmpty()) {
			isAvailable = false;
			ThreadPoolUtils.removeThreadPool(threadPool, listenSocket);
		}
	}

	@Override
	public boolean isEmpty() {
		return tcpNodes.isEmpty();
	}

	private void checkSocketException(IOException exception) {
		if (exception instanceof SocketException &&
			exception.getMessage().equals(NodeMessageUtil.getSocketInterruptMessage(Protocol.TCP))) {
			log.info("TcpNodeGroup: Stop Forward - {}", this);
			return;
		}
		log.error("TcpNodeGroup: Error occur in startForward\n{}", Arrays.toString(exception.getStackTrace()));
		throw new IllegalThreadStateException("패킷을 받을 수 없습니다 " + exception.getMessage());
	}

	private synchronized TcpNode selectNode() {
		TcpNode curNode = tcpNodes.poll();
		tcpNodes.offer(curNode);
		return curNode;
	}

}
