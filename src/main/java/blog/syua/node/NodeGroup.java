package blog.syua.node;

import java.io.IOException;
import java.util.Collection;

import blog.syua.node.managerimpl.TcpNodeGroup;
import blog.syua.node.managerimpl.UdpNodeGroup;

public interface NodeGroup {

	static NodeGroup newInstance(Protocol protocol, int port) throws IOException {
		if (protocol.equals(Protocol.TCP)) {
			return new TcpNodeGroup(port);
		}
		if (protocol.equals(Protocol.UDP)) {
			return new UdpNodeGroup(port);
		}
		throw new IllegalArgumentException("NodeManager를 생성할 수 없습니다");
	}

	void startForwarding() throws IOException;

	Collection<Node> getNodes();

	void registerNode(Node tcpNode);

	void unRegisterNode(Node tcpNode);

}
