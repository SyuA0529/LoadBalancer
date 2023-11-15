package blog.syua.control.dto;

import blog.syua.control.ControlType;
import blog.syua.node.node.Protocol;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ControlRequest {

	private ControlType cmd;
	private Protocol protocol;
	private int port;

}
