package com.wizkterm.app.terminal;

import android.app.Service;

import androidx.annotation.NonNull;

import com.wizkterm.app.TermuxService;
import com.wizkterm.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.wizkterm.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.wizkterm.terminal.TerminalSession;
import com.wizkterm.terminal.TerminalSessionClient;

/** The {@link TerminalSessionClient} implementation that may require a {@link Service} for its interface methods. */
public class TermuxTerminalSessionServiceClient extends TermuxTerminalSessionClientBase {

    private static final String LOG_TAG = "TermuxTerminalSessionServiceClient";

    private final TermuxService mService;

    public TermuxTerminalSessionServiceClient(TermuxService service) {
        this.mService = service;
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxSession termuxSession = mService.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }

}
