package blog.syua.node.groupmanager;

import blog.syua.node.node.Node;

public interface NodeManagingCallback {

	void registerListener(NodeManagingListener listener);

	void notifyRegisterNode(Node node);

	void notifyUnRegisterNode(Node node);

}
