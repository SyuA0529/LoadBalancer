package blog.syua.node.groupmanager;

import java.io.IOException;

import blog.syua.node.node.Protocol;

public interface NodeGroupManager extends NodeManagingCallback {

	void registerNode(Protocol protocol, String ipAddr, int port) throws IOException;

	void unRegisterNode(Protocol protocol, String ipAddr, int port) throws IOException;

}
