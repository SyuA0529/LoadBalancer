package blog.syua.control.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public class ControlSuccessResponse extends ControlResponse {

	private String ack;

}
