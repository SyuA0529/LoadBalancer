package blog.syua.control.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ControlFailResponse extends ControlResponse {

	private static final String FAIL_ACK = "failed";


	private String ack = FAIL_ACK;
	private String msg;

	public ControlFailResponse(String msg) {
		this.msg = msg;
	}

}
