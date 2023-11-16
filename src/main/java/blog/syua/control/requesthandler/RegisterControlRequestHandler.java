package blog.syua.control.requesthandler;

import java.net.InetAddress;

import org.springframework.stereotype.Component;

import blog.syua.control.ControlType;
import blog.syua.control.dispatcher.ControlRequestDispatcher;
import blog.syua.control.dto.ControlFailResponse;
import blog.syua.control.dto.ControlRequest;
import blog.syua.control.dto.ControlResponse;
import blog.syua.control.dto.ControlSuccessResponse;
import blog.syua.node.groupmanager.NodeGroupManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RegisterControlRequestHandler extends ControlRequestHandler {

	private static final String FAIL_MSG = "Registration Failed";

	private final NodeGroupManager nodeGroupManager;

	public RegisterControlRequestHandler(ControlRequestDispatcher controlRequestDispatcher,
		NodeGroupManager nodeGroupManager) {
		super(controlRequestDispatcher);
		this.nodeGroupManager = nodeGroupManager;
	}

	@Override
	public ControlType getHandleType() {
		return ControlType.REGISTER;
	}

	@Override
	public ControlResponse handleRequest(ControlRequest controlRequest, InetAddress ipAddr) {
		log.info("Start Register Request - {}", ipAddr);
		if (!controlRequest.getCmd().equals(ControlType.REGISTER)) {
			log.info("It's not a Register request - {}", controlRequest);
			return new ControlFailResponse(FAIL_MSG);
		}
		try {
			nodeGroupManager.registerNode(controlRequest.getProtocol(), ipAddr, controlRequest.getPort());
			log.info("Success Registeration - {} {}", ipAddr, controlRequest);
			return new ControlSuccessResponse();
		} catch (Exception e) {
			log.info("Error occur in handleRequest - {}", controlRequest);
			e.printStackTrace();
		}
		return new ControlFailResponse(FAIL_MSG);
	}

}
