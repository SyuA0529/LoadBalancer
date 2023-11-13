package blog.syua.node.groupmanager;

import blog.syua.node.node.Node;

public interface NodeManagingListener {

	void onRegisterNode(Node node);

	void onUnRegisterNode(Node node);

}
