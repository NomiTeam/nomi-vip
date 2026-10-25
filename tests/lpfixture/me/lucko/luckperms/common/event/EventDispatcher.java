package me.lucko.luckperms.common.event;
/** Offline no-listener event sink; actual permission holder/node maps remain unmodified. */
public final class EventDispatcher {
 public void dispatchNodeChanges(me.lucko.luckperms.common.model.PermissionHolder holder,
  net.luckperms.api.model.data.DataType type,
  me.lucko.luckperms.common.util.Difference<net.luckperms.api.node.Node> difference) {}
}
