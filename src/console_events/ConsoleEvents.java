package console_events;


import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION;
import com.sun.jna.platform.win32.WinBase.STARTUPINFO;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;

public class ConsoleEvents {
	
	static String JAVA_CMDLINE_BASE = String.format(
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
	static PROCESS_INFORMATION create_process(String cmdline, int flags, boolean inherit_std_handles) {
		STARTUPINFO si = new STARTUPINFO();
		PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
		
		if (inherit_std_handles) {
			si.dwFlags = Kernel32.STARTF_USESTDHANDLES | Kernel32.STARTF_USESHOWWINDOW;
			si.hStdInput = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
			si.hStdOutput = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
			si.hStdError = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_ERROR_HANDLE);
			si.wShowWindow = new WORD(User32.SW_HIDE);
		}
		
		Kernel32.INSTANCE.CreateProcess(
				null, cmdline, null, null, inherit_std_handles,
				new DWORD(flags), null, null, si, pi);
		return pi;
	}
	
	static PROCESS_INFORMATION create_child(String cmdline, boolean inherit_std_handles) {
		PROCESS_INFORMATION pi = create_process(cmdline, Kernel32.CREATE_NEW_CONSOLE, inherit_std_handles);
		System.out.println("+   Child process started: " + pi.dwProcessId);
		sleep(1000);
		return pi;
	}
	
	static void terminate_child(PROCESS_INFORMATION pi) {
		int pid = pi.dwProcessId.intValue();
		create_process(
				String.format("%s %s %d", JAVA_CMDLINE_BASE, "send_ctrl_c", pid),
				Kernel32.DETACHED_PROCESS,
				true);
		Kernel32.INSTANCE.WaitForSingleObject((HANDLE) pi.hProcess, Kernel32.INFINITE);
		System.out.println("+   Child process terminated: " + pid);
		// close handles to avoid resource leaks (zombie processes)
		Kernel32.INSTANCE.CloseHandle((HANDLE) pi.hProcess);
		Kernel32.INSTANCE.CloseHandle((HANDLE) pi.hThread);
	}
	
	static void main_child(String[] args) {
		String message = "++  Hello! I am " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
		Kernel32.INSTANCE.SetConsoleTitle(message);
		System.out.println(message);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				String message = "++  Bye Bye " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
				Kernel32.INSTANCE.SetConsoleTitle(message);
				System.out.println(message);
				ConsoleEvents.sleep(1000);
			}
		});
		Kernel32.INSTANCE.WaitForSingleObject(Kernel32.INSTANCE.GetCurrentProcess(), Kernel32.INFINITE);
	}
	
	static void main_send_ctrl_c(String[] args) {
		// needs to be created with DETACHED_PROCESS
		System.out.println(String.format("%s %s", "+++ Send CTRL_C_EVENT to", args[1]));
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
		
		String message = "+   Hello! I am " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
		Kernel32.INSTANCE.SetConsoleTitle(message);
		System.out.println(message);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				String message = "+   Bye Bye " + Kernel32.INSTANCE.GetCurrentProcessId() + ".";
				Kernel32.INSTANCE.SetConsoleTitle(message);
				System.out.println(message);
				ConsoleEvents.sleep(2000);
			}
		});
		
		// start child processes
		
		PROCESS_INFORMATION children[] = new PROCESS_INFORMATION[10];
		int ii = 0;
		
		children[ii++] = create_child("cmd.exe /c test.cmd", false);
		children[ii++] = create_child("ping -t 127.0.0.1", true);
		
		for (int ik = 0; ik < 3; ++ik) {
			children[ii++] = create_child(String.format("%s %s", JAVA_CMDLINE_BASE, "child"), true);
		}
		
		children[ii++] = create_child("cmd.exe /c \"echo Be patient! & timeout /t -1 /nobreak\"", false);
		
		sleep(2500);
		
		// send signals to gracefully shutdown
		
		for (; ii-- > 0; ) {
			terminate_child(children[ii]);
		}
	}
}
