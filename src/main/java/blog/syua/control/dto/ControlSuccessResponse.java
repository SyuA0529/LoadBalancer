package blog.syua.control.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class ControlSuccessResponse extends ControlResponse {

	public static final String SUCCESS_ACK = "successful";

	public ControlSuccessResponse() {
		super(SUCCESS_ACK);
	}

}
