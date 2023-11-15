package blog.syua.control.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class ControlFailResponse extends ControlResponse {

	public static final String FAIL_ACK = "failed";

	private final String msg;

	public ControlFailResponse() {
		super(FAIL_ACK);
		msg = "Fail";
	}

	public ControlFailResponse(String msg) {
		super(FAIL_ACK);
		this.msg = msg;
	}

}
