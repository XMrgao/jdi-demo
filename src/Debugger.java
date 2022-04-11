import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ModificationWatchpointRequest;
import com.sun.tools.jdi.SocketAttachingConnector;

import java.util.List;
import java.util.Map;

public class Debugger {
    //要调试的目标程序的监听地址
    public static final String HOST = "127.0.0.1";
    //要调试的目标程序的监听端口
    public static final String PORT = "20020";
    //要调试的目标程序的类全名
    public static final String CLS_NAME = "Main";
    //类中要监控的变量字段名
    public static final String CLS_FIELD = "value";
    //监测到 watchCount 次变量修改事件后退出程序
    public static int watchCount = 3;

    public static void main(String[] args) throws Exception {
        // 样板代码，创建与调试目标程序的连接
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        List<AttachingConnector> connectors = vmm.attachingConnectors();
        SocketAttachingConnector sac = null;
        for(AttachingConnector ac:connectors) {
            if(ac instanceof SocketAttachingConnector) {
                sac = (SocketAttachingConnector) ac;
                break;
            }
        }
        if(sac == null) {
            throw new Exception("未找到SocketAttachingConnector连接器");
        }
        Map<String, Connector.Argument> arguments = sac.defaultArguments();
        arguments.get("hostname").setValue(HOST);
        arguments.get("port").setValue(PORT);
        VirtualMachine vm = sac.attach(arguments);


        // 连接成功后，根据类名，拿到类的引用对象
        List<ReferenceType> classesByName = vm.classesByName(CLS_NAME);
        if (classesByName == null || classesByName.isEmpty()) {
            System.out.println("未找到"+CLS_NAME);
            return;
        }

        // 设置跟踪类型为事件
        vm.setDebugTraceMode(VirtualMachine.TRACE_EVENTS);
        // 让虚拟机恢复运行
        vm.resume();

        EventRequestManager eventRequestManager = vm.eventRequestManager();
        // 根据字段创建一个监控变量变化请求。还有创建其他类型断点的请求，具体请看文档：https://nowjava.com/docs/java-jdk-14/api/jdk.jdi/com/sun/jdi/request/EventRequestManager.html
        ModificationWatchpointRequest request = eventRequestManager.createModificationWatchpointRequest(classesByName.get(0).fieldByName(CLS_FIELD));
        /**
         * 设置出现事件时线程挂起策略：挂起事件线程，如果不挂起，会无法获得准确的线程调用堆栈，有三个值可选：
         *      1. EventRequest.SUSPEND_NONE  不挂起，适合只想要一个事件通知，不用保留现场，对服务器影响最小
         *      2. EventRequest.SUSPEND_EVENT_THREAD  挂起发生事件的线程，其他线程不挂起，我因为要获得事件发生时的堆栈，所以选择了这个。
         *          这个也根据个人情况谨慎使用,有注意点，看文档：https://nowjava.com/docs/java-jdk-14/api/jdk.jdi/com/sun/jdi/ThreadReference.html#suspend()
         *          ThreadReference.suspend() 后，如果挂起的线程包含另一个正在运行的线程所需的监视器，在挂起的线程再次恢复之前，目标虚拟机中可能会出现死锁
         *      3. EventRequest.SUSPEND_ALL 全部挂起，正式环境慎用，会导致所有业务停止
         */
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.enable();


        // 死循环不停的获取事件
        eventLoop:while (true) {
            EventSet eventSet = vm.eventQueue().remove();
            EventIterator eventIterator = eventSet.eventIterator();
            while (eventIterator.hasNext()) {
                Event event = eventIterator.next();
                // 发生了变量值被修改的事件
                if (event instanceof ModificationWatchpointEvent) {
                    ModificationWatchpointEvent e = (ModificationWatchpointEvent) event;
                    // 获得事件线程引用
                    ThreadReference thread = e.thread();
                    // 打印被监控变量当前值
                    System.out.println(e.field().toString()+"发生变化："+e.valueToBe().toString());

                    // 打印线程调用堆栈
                    System.out.println("stack trace in thread \""+thread.name()+"\"");
                    for (int i = thread.frameCount() - 1 ; i >= 0 ; i --) {
                        Location location = thread.frame(i).location();
                        System.out.println("\tat "+location.method() +" -> ("+location.sourceName()+":"+location.lineNumber()+")");
                    }
                    // 工作结束后，切记一定要让线程恢复运行！！！
                    thread.resume();
                    eventSet.resume();

                    watchCount --;

                    if(watchCount <= 0){
                        break eventLoop;
                    }
                }
            }
        }
        vm.dispose();
    }
}
