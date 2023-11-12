package blog.syua.node;

import java.io.IOException;
import java.util.Collection;

import blog.syua.node.managerimpl.TcpNodeManager;
import blog.syua.node.managerimpl.UdpNodeManager;

public interface NodeManager {

	static NodeManager newInstance(Protocol protocol, int port) throws IOException {
		if (protocol.equals(Protocol.TCP)) {
			return new TcpNodeManager(port);
		}
		if (protocol.equals(Protocol.UDP)) {
			return new UdpNodeManager(port);
		}
		throw new IllegalArgumentException("NodeManager를 생성할 수 없습니다");
	}

	void startForwarding() throws IOException;

	Collection<Node> getNodes();

	void registerNode(Node tcpNode);

	void unRegisterNode(Node tcpNode);

}
