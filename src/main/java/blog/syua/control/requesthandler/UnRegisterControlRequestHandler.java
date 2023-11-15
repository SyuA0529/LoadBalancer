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
public class UnRegisterControlRequestHandler extends ControlRequestHandler {

	private static final String FAIL_MSG = "UnRegistration Failed";

	private final NodeGroupManager nodeGroupManager;

	public UnRegisterControlRequestHandler(ControlRequestDispatcher controlRequestDispatcher,
		NodeGroupManager nodeGroupManager) {
		super(controlRequestDispatcher);
		this.nodeGroupManager = nodeGroupManager;
	}

	@Override
	public ControlType getHandleType() {
		return ControlType.UNREGISTER;
	}

	@Override
	public ControlResponse handleRequest(ControlRequest controlRequest, InetAddress ipAddr) {
		if (!controlRequest.getCmd().equals(ControlType.UNREGISTER)) {
			log.info("It's not a UnRegister request - {}", controlRequest);
			return new ControlFailResponse(FAIL_MSG);
		}
		try {
			nodeGroupManager.unRegisterNode(controlRequest.getProtocol(), ipAddr, controlRequest.getPort());
			return new ControlSuccessResponse();
		} catch (Exception e) {
			log.info("Error occur in handleRequest - {}", controlRequest);
			e.printStackTrace();
			return new ControlFailResponse(FAIL_MSG);
		}
	}

}
