package blog.syua.node.group;

import blog.syua.node.node.Node;
import blog.syua.node.node.Protocol;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class ForwardInfo {

	private final Protocol protocol;
	private final int port;

	private ForwardInfo(Protocol protocol, int port) {
		this.protocol = protocol;
		this.port = port;
	}

	private ForwardInfo(Node node) {
		this.protocol = node.getProtocol();
		this.port = node.getPort();
	}

	public static ForwardInfo of(Protocol protocol, int port) {
		return new ForwardInfo(protocol, port);
	}

	public static ForwardInfo of(Node node) {
		return new ForwardInfo(node);
	}

}
