package com.vattima.lego.command.lego.command;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command
@Slf4j
public class MainCommand implements Runnable
{
    @CommandLine.Option(names = {"-v", "--version"}, description = "display version info")
    boolean versionRequested;

    @Override
    public void run() {
        log.info("MainCommand");
    }
}