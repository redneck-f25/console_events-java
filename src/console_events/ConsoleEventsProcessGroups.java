// This doesn't work. We cannot handle SIGBRAEK.

package console_events;

// http://www.oracle.com/technetwork/java/javase/signals-139944.html

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION;
import com.sun.jna.platform.win32.WinBase.STARTUPINFO;
import com.sun.jna.platform.win32.WinDef.DWORD;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ConsoleEventsProcessGroups {
	
	static String CMDLINE_BASE = String.format(
			"\"%s%s\" %s -Dfile.encoding=%s -classpath \"%s\" %s",
			System.getProperty("java.home"),
			"\\bin\\java.exe",
			"-Xrs",
			System.getProperty("file.encoding"),
			System.getProperty("java.class.path"),
			ConsoleEventsProcessGroups.class.getName()
			);

	static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
	
	// wrap around CreateProcess()
	static int create_process(String cmdline, int flags) {
		STARTUPINFO si = new STARTUPINFO();
		PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
		
		Kernel32.INSTANCE.CreateProcess(
				null, cmdline, null, null, false,
				new DWORD(flags), null, null, si, pi);
		//close handles immediately to avoid resource leaks
		Kernel32.INSTANCE.CloseHandle(pi.hProcess);
		Kernel32.INSTANCE.CloseHandle(pi.hThread);
		return pi.dwProcessId.intValue();
	}
	
	static int create_child(String cmdline) {
		int child_pid = create_process(cmdline, Kernel32.CREATE_NEW_PROCESS_GROUP);
		System.out.println("Child process started: " + child_pid);
		sleep(1000);
		return child_pid;
	}
	
	static void terminate_child(int child_pid) {
		Kernel32.INSTANCE.GenerateConsoleCtrlEvent(Kernel32.CTRL_BREAK_EVENT, child_pid);
		System.out.println("Child process terminated: " + child_pid);
		sleep(1500);
	}
	
	static void main_child(String[] args) {
		String message = "Hello! I am " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
		Kernel32.INSTANCE.SetConsoleTitle(message);
		System.out.println(message);
		Signal.handle(new Signal("BREAK"), new SignalHandler() {
			public void handle(Signal sig) {
				String message = "Bye Bye " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
				Kernel32.INSTANCE.SetConsoleTitle(message);
				System.out.println(message);
				ConsoleEventsProcessGroups.sleep(1000);
			}
		});
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				String message = "Bye Bye " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
				Kernel32.INSTANCE.SetConsoleTitle(message);
				System.out.println(message);
				ConsoleEventsProcessGroups.sleep(1000);
			}
		});
		Kernel32.INSTANCE.WaitForSingleObject(Kernel32.INSTANCE.GetCurrentProcess(), Kernel32.INFINITE);
	}
	
	public static void main(String[] args) {		
		if ( args.length > 0 ) {
			if (args[0].equals("child")) {
				main_child(args);
				return;
			}
			return;
		}
		
		// Make sure we are attached to a console (javaw.exe).
		// If we already have console (java.exe) this call will fail. Don't care.
		Kernel32.INSTANCE.AllocConsole();
		
		// start child processes
		
		int child_pids[] = new int[10];
		int ii = 0;
		
		//child_pids[ii++] = create_child("cmd.exe /c test.cmd");
		//child_pids[ii++] = create_child("ping -t 127.0.0.1");
		
		for (int ik = 0; ik < 1; ++ik) {
			child_pids[ii++] = create_child(String.format("%s %s", CMDLINE_BASE, "child"));
		}
		
		//child_pids[ii++] = create_child("cmd.exe /c \"echo Be patient! & timeout /t -1 /nobreak\"");
		
		sleep(2500);
		
		// send signals to gracefully shutdown
		
		for (; ii-- > 0; ) {
			terminate_child(child_pids[ii]);
		}
		
		sleep(4000);
	}
}
