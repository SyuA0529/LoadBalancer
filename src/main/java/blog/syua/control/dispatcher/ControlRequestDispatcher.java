package blog.syua.control.dispatcher;

import java.net.Socket;

import blog.syua.control.ControlType;
import blog.syua.control.requesthandler.ControlRequestHandler;

public interface ControlRequestDispatcher {

	void registerHandler(ControlRequestHandler controlRequestHandler);

	ControlRequestHandler getControlRequestHandler(ControlType controlType);

	void dispatcherControlRequest(Socket nodeSocket);

}
