# Storage Explorer Angular Client

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.1.5.

## Development Server

To start a local development server, run:

```bash
ng serve
```

## Development Backend

The project is set up to use a local backend for development. The backend is expected to run on
`http://localhost:8080` and provide the Storage Explorer REST API at
`http://Localhost:8080/storageexplorer`.

This repository provides such a backend server for convenience: the `app-demo` subproject. To use it:

1. Build the `spring-boot-starter` subproject by executing `./gradlew spring-boot-starter:build` in the repository root.
2. Refresh your Gradle project in your IDE.
3. Specify the following properties in `app-demo`'s `application.properties`:
    ```properties
      storage-explorer.api-path=/storageexplorer
      fs.base.directory=[[[Path to your local smartbit4all file system storage to be used by the demo backend]]]
    ```
4. Declare dependency to your newly build `spring-boot-starter` subproject in `app-demo`'s `build.gradle`:
    ```groovy
       implementation files('../spring-boot-starter/build/libs/spring-boot-starter-[[[The current SemVer]]].jar')
    ```
5. Run the `app-demo` subproject.

You may set the username and password property values as you wish.

## Development Considerations

### Access to the Backend

Please never commit changes to `index.html`, `proxy.conf.json` and the routing-related parts
of `app.config.ts`. Client code may never hardcode any path information relating to accessing
the backend.

### Component Styles

- Components are written using the single-file component (SFC) format: never declare separate template and stylesheet files.
- Components always present on the main layout go into the `layout` folder. Simple (dumb) components and dialogues related to the main layout go into the `components` folder.
- Components representing routes within the main layout go into the `pages` folder. Every such "page" sits in its own folder, surrounded by it's related subcomponents.
- Certain aspects of application logic may warrant the definition of a service. One `.ts` file may contain only one service, but this file should also contain the related top level functions and constants related to the aspect.


