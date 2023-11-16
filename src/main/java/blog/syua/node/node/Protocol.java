package blog.syua.node.node;

import lombok.Getter;

@Getter
public enum Protocol {

	TCP(Integer.MAX_VALUE),
	UDP(0xFFFF);

	private final int maxReceiveSize;

	Protocol(int maxReceiveSize) {
		this.maxReceiveSize = maxReceiveSize;
	}

}
