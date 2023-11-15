package blog.syua.node.node;

import java.net.InetAddress;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public abstract class Node {

	private final InetAddress ipAddr;
	private final int port;

	protected Node(InetAddress ipAddr, int port) {
		this.ipAddr = ipAddr;
		this.port = port;
	}

	public static Node newInstance(Protocol protocol, InetAddress ipAddr, int port) {
		if (protocol.equals(Protocol.TCP)) {
			return new TcpNode(ipAddr, port);
		}
		if (protocol.equals(Protocol.UDP)) {
			return new UdpNode(ipAddr, port);
		}
		throw new IllegalArgumentException("Cannot create Node");
	}

	public abstract Protocol getProtocol();

	public abstract boolean isHealthy();

	@Override
	public String toString() {
		return "Node{" +
			"ipAddr=" + ipAddr +
			", port=" + port +
			'}';
	}

}
