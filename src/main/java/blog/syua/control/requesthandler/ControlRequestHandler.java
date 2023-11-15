package blog.syua.control.requesthandler;

import java.io.IOException;
import java.net.InetAddress;

import blog.syua.control.ControlType;
import blog.syua.control.dispatcher.ControlRequestDispatcher;
import blog.syua.control.dto.ControlRequest;
import blog.syua.control.dto.ControlResponse;

public abstract class ControlRequestHandler {

	protected ControlRequestHandler(ControlRequestDispatcher controlRequestDispatcher) {
		controlRequestDispatcher.registerHandler(this);
	}

	public abstract ControlType getHandleType();

	public abstract ControlResponse handleRequest(ControlRequest controlRequest, InetAddress ipAddr) throws IOException;

}
