/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.cli.command;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.Availability;
import org.springframework.shell.AvailabilityProvider;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.completion.CompletionProvider;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;

@Configuration
public class CommandConfig {

  @Bean(CommandConstants.REQUIRES_STORAGE)
  AvailabilityProvider avProvRequiresStorage(StorageInstanceContext storageInstanceContext) {
    return () -> storageInstanceContext.current().isPresent()
        ? Availability.available()
        : Availability.unavailable("No storage instance selected.");
  }

  @Bean(CommandConstants.COMPLETION_PROPOSAL_URI)
  CompletionProvider uriCompletionProvider(StorageInstanceContext storageInstanceContext) {
    return ctx -> storageInstanceContext.current()
        .map(it -> Stream
            .concat(it.index().uris().stream(), storageInstanceContext.completionSet().stream())
            .map(String::valueOf)
            .filter(uri -> uri.contains(ctx.currentWord()))
            .map(CompletionProposal::new)
            .toList())
        .orElseGet(Collections::emptyList);
  }

  @Bean(CommandConstants.COMPLETION_PROPOSAL_VERSION)
  CompletionProvider versionCompletionProvider() {
    return ctx -> List.of(new CompletionProposal("0"), new CompletionProposal("LATEST"));
  }

}
