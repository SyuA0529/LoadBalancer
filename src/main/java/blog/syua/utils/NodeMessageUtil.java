package blog.syua.utils;

import java.nio.charset.StandardCharsets;

public class NodeMessageUtil {

	private static final String FORWARD_ERROR_MESSAGE = "패킷 포워딩에 실패하였습니다";

	private NodeMessageUtil() {
	}

	public static byte[] getForwardErrorMessage() {
		return FORWARD_ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8);
	}

}
