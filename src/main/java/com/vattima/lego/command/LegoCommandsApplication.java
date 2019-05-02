package com.vattima.lego.command;

import com.vattima.lego.command.lego.command.MainCommand;
import com.vattima.lego.command.lego.command.ManifestsCommand;
import com.vattima.lego.imaging.service.AlbumManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fusesource.jansi.AnsiConsole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@SpringBootApplication(scanBasePackages = {"net.bricklink", "com.bricklink", "com.vattima"})
@Slf4j
public class LegoCommandsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegoCommandsApplication.class, args);
    }

	@Component
	@RequiredArgsConstructor
	public static class CommandLineRunner implements ApplicationRunner {
    	private final AlbumManager albumManager;

		@Override
		public void run(ApplicationArguments args) throws Exception {
			AnsiConsole.systemInstall();
			CommandLine commandLine = new CommandLine(new MainCommand());
			commandLine.addSubcommand("manifests", new ManifestsCommand(albumManager));
            List<Object> result = commandLine.parseWithHandler(new CommandLine.RunAll(), args.getSourceArgs());
            log.info("parsed [{}]", result);

			AnsiConsole.systemUninstall();
		}
	}
}
