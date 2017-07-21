package console_events;


import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION;
import com.sun.jna.platform.win32.WinBase.STARTUPINFO;
import com.sun.jna.platform.win32.WinDef.DWORD;

public class ConsoleEvents {
	
	static String CMDLINE_BASE = String.format(
			"\"%s%s\" -Dfile.encoding=%s -classpath \"%s\" %s",
			System.getProperty("java.home"),
			"\\bin\\java.exe",
			System.getProperty("file.encoding"),
			System.getProperty("java.class.path"),
			ConsoleEvents.class.getName()
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
		int child_pid = create_process(cmdline, Kernel32.CREATE_NEW_CONSOLE);
		System.out.println("Child process started: " + child_pid);
		sleep(1000);
		return child_pid;
	}
	
	static void terminate_child(int child_pid) {
		create_process(
				String.format("%s %s %d", CMDLINE_BASE, "send_ctrl_c", child_pid),
				Kernel32.DETACHED_PROCESS);
		System.out.println("Child process terminated: " + child_pid);
		sleep(1500);
	}
	
	static void main_child(String[] args) {
		Kernel32.INSTANCE.SetConsoleTitle("Hello! I am " + Kernel32.INSTANCE.GetCurrentProcessId() + ".");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				Kernel32.INSTANCE.SetConsoleTitle("Bye Bye " + Kernel32.INSTANCE.GetCurrentProcessId() + ".");
				ConsoleEvents.sleep(1000);
			}
		});
		Kernel32.INSTANCE.WaitForSingleObject(Kernel32.INSTANCE.GetCurrentProcess(), Kernel32.INFINITE);
	}
	
	static void main_send_ctrl_c(String[] args) {
		// needs to be created with DETACHED_PROCESS
		Kernel32.INSTANCE.AttachConsole(Integer.parseInt(args[1]));
		Kernel32.INSTANCE.GenerateConsoleCtrlEvent(Kernel32.CTRL_C_EVENT, 0);
	}
	
	public static void main(String[] args) {
		
		if ( args.length > 0 ) {
			if (args[0].equals("child")) {
				main_child(args);
				return;
			}
			if (args[0].equals("send_ctrl_c")) {
				if ( args.length > 1 ) {
					main_send_ctrl_c(args);
					return;
				}
			}
			return;
		}
		
		// start child processes
		
		int child_pids[] = new int[10];
		int ii = 0;
		
		child_pids[ii++] = create_child("cmd.exe /c test.cmd");
		child_pids[ii++] = create_child("ping -t 127.0.0.1");
		
		for (int ik = 0; ik < 3; ++ik) {
			child_pids[ii++] = create_child(String.format("%s %s", CMDLINE_BASE, "child"));
		}
		
		child_pids[ii++] = create_child("cmd.exe /c \"echo Be patient! & timeout /t -1 /nobreak\"");
		
		sleep(2500);
		
		// send signals to gracefully shutdown
		
		for (; ii-- > 0; ) {
			terminate_child(child_pids[ii]);
		}
		
		sleep(4000);
	}

}
