package com.aestallon.storageexplorer.client.userconfig.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.client.util.OpResult;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.NominalType;
import com.aestallon.storageexplorer.core.model.type.dto.EntityTypeDto;
import com.aestallon.storageexplorer.core.service.type.YamlSchemaExtractor;

/*
 *[...]/storage-explorer/
 *            ┃
 *            ┣━━━━━type-info
 *            ┃         ┃
 *            ┃         ┣━━━━━[storage-id-1]                <-- one subfolder per storage
 *            ┃         ┃
 *            ┃         ┣━━━━━[storage-id-2]
 *            ┃         ┃            ┃
 *            ┃        ...           ┗━━━━━type.info.json   <-- contains the discovered structural
 *            ┃                          type information
 *            ┗━━━━━━━━yaml
 *                      ┃                                    ⇣
 *                      ┣━━━━━[1-api.yaml]                     contains the YAML files
 *                      ┃                                      describing the nominal type
 *                      ┣━━━━━[2-api.yaml]                     information
 *                      ┃                                    ⇡
 *                     ...
 */
public final class TypeInfoRepository {

  private static final String FOLDER_TYPE_INFO = "type-info";
  private static final String FOLDER_YAML = "yaml";

  private static final String FILE_TYPE_INFO = "type.info.json";
  private static final Logger log = LoggerFactory.getLogger(TypeInfoRepository.class);

  private static void write(Path path, String text) throws IOException {
    Files.writeString(
        path,
        text,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private final Path settingsFolder;

  TypeInfoRepository(Path settingsFolder) {
    this.settingsFolder = settingsFolder;
  }

  private Path storageDir(final StorageId storageId) throws IOException {
    final Path storagePath = settingsFolder.resolve(FOLDER_TYPE_INFO).resolve(storageId.toString());
    return Files.createDirectories(storagePath);
  }

  private Path yamlDir() throws IOException {
    final Path yamlDirPath = settingsFolder.resolve(FOLDER_YAML);
    return Files.createDirectories(yamlDirPath);
  }

  public OpResult saveStructuredTypeInfo(final StorageId storageId,
                                         final Collection<EntityType> types) {
    final var opTitle = "Saving structured type info";
    try {

      final var orderedTypes = types.stream()
          .sorted(Comparator.comparing(EntityType::name))
          .map(EntityTypeDto::of)
          .toList();
      final var json = UserConfigPersistenceService.OBJECT_MAPPER
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(orderedTypes);

      final var storageDir = storageDir(storageId);
      final var typeInfoFile = storageDir.resolve(FILE_TYPE_INFO);
      write(typeInfoFile, json);
      return OpResult.ok(opTitle, "Structured type info saved.");

    } catch (final IOException e) {
      return OpResult.err(opTitle, e);
    }
  }

  public List<EntityType> loadStructuredTypeInfo(final StorageId storageId) {

    try {

      final var storageDir = storageDir(storageId);
      final var typeInfoFile = storageDir.resolve(FILE_TYPE_INFO);
      if (!Files.exists(typeInfoFile)) {
        log.debug("No structured type info found for storage [ {} ]", storageId);
        return Collections.emptyList();
      }

      try (final var in = Files.newInputStream(typeInfoFile)) {
        final List<EntityTypeDto> dtos = UserConfigPersistenceService.OBJECT_MAPPER
            .readerForListOf(EntityTypeDto.class)
            .readValue(in);
        return dtos.stream().map(EntityTypeDto::toDomainObject).toList();
      }

    } catch (IOException e) {
      log.error("Could not load structured type info for storage [ {} ]", storageId);
      log.warn(e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  public boolean yamlExists(final String yamlName) {
    try {
      final var yamlDir = yamlDir();
      final var yamlPath = yamlDir.resolve(yamlName);
      return Files.exists(yamlPath);
    } catch (IOException e) {
      log.error("Could not check if YAML file [ {} ] exists!", yamlName);
      return false;
    }
  }

  public Pair<String, Map<String, NominalType.Obj>> saveYaml(final Path yaml) {
    try {
      final var yamlDir = yamlDir();
      final var filename = yaml.getFileName().toString();
      final var yamlPath = yamlDir.resolve(filename);
      Files.copy(yaml, yamlPath);

      final var extractor = new YamlSchemaExtractor();
      final var types = extractor.extract(Files.readString(yamlPath, StandardCharsets.UTF_8));
      return Pair.of(filename, types);

    } catch (final IOException e) {
      log.error("Could not save YAML file [ {} ]: {}", yaml, e.getMessage());
      log.debug(e.getMessage(), e);
      return null;
    }
  }

  public Map<String, Map<String, NominalType.Obj>> loadYamls() {
    try {
      final var yamlDir = yamlDir();
      try (final var files = Files.list(yamlDir)) {
        return files
            .filter(it -> it.getFileName().endsWith(".yaml") || it.getFileName().endsWith(".yml"))
            .<Pair<String, Optional<Map<String, NominalType.Obj>>>>map(it -> {
              final var filename = it.getFileName().toString();
              try {
                final var extractor = new YamlSchemaExtractor();
                final var types = extractor.extract(Files.readString(it, StandardCharsets.UTF_8));
                return Pair.of(filename,
                    Optional.of(types));
              } catch (final IOException e) {
                log.error("Could not load YAML file [ {} ]: {}", it, e.getMessage());
                log.debug(e.getMessage(), e);
                return Pair.of(filename,
                    Optional.empty());
              }
            })
            .flatMap(Pair.streamOnB())
            .collect(Pair.toMap());
      }
    } catch (final IOException e) {
      log.error("Could not load YAML files: {}", e.getMessage());
      log.debug(e.getMessage(), e);
      return Collections.emptyMap();
    }
  }



}
