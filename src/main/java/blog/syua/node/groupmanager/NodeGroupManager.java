package blog.syua.node.groupmanager;

import java.io.IOException;
import java.net.InetAddress;

import blog.syua.node.node.Protocol;

public interface NodeGroupManager extends NodeManagingCallback {

	void registerNode(Protocol protocol, InetAddress ipAddr, int port) throws IOException;

	void unRegisterNode(Protocol protocol, InetAddress ipAddr, int port);

}
