package blog.syua.node;

import java.net.InetAddress;
import java.net.UnknownHostException;

import blog.syua.node.nodeimpl.TcpNode;
import blog.syua.node.nodeimpl.UdpNode;

public abstract class Node {

	private final InetAddress ipAddr;
	private final int port;

	protected Node(String ipAddr, int port) throws UnknownHostException {
		this.ipAddr = InetAddress.getByName(ipAddr);
		this.port = port;
	}

	public static Node newInstance(Protocol protocol, String ipAddr, int port) throws UnknownHostException {
		if (protocol.equals(Protocol.TCP)) {
			return new TcpNode(ipAddr, port);
		}
		if (protocol.equals(Protocol.UDP)) {
			return new UdpNode(ipAddr, port);
		}
		throw new IllegalArgumentException("Node를 생성할 수 없습니다");
	}

	public abstract Protocol getProtocol();

	public InetAddress getIpAddr() {
		return ipAddr;
	}

	public int getPort() {
		return port;
	}

	public abstract boolean isHealthy();

}
