package blog.syua.node.group;

import java.io.IOException;

import blog.syua.node.node.Node;
import blog.syua.node.node.Protocol;

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

	void registerNode(Node tcpNode);

	void unRegisterNode(Node tcpNode);

	boolean isEmpty();

}
