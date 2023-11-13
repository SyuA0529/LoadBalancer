package blog.syua.utils;

import java.nio.charset.StandardCharsets;

import blog.syua.node.node.Protocol;

public class NodeMessageUtil {

	private static final String TCP_SOCKET_INTERRUPT_MESSAGE = "Interrupted function call: accept failed";
	private static final String UDP_SOCKET_INTERRUPT_MESSAGE = "socket closed";
	private static final String FORWARD_ERROR_MESSAGE = "패킷 포워딩에 실패하였습니다";

	private NodeMessageUtil() {
	}

	public static String getSocketInterruptMessage(Protocol protocol) {
		if (protocol.equals(Protocol.TCP)) {
			return TCP_SOCKET_INTERRUPT_MESSAGE;
		}
		if (protocol.equals(Protocol.UDP)) {
			return UDP_SOCKET_INTERRUPT_MESSAGE;
		}
		return null;
	}

	public static byte[] getForwardErrorMessage() {
		return FORWARD_ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8);
	}

}
