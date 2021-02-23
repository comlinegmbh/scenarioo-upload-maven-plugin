# Scenarioo upload maven plugin

A plugin for uploading Scenarioo reports to the viewer app.

The Scenarioo documentation mentions the [requirements for uploading reports](http://scenarioo.org/docs/master/tutorial/Publish-Documentation-Data.html)
into the viewer. This maven plugin automates the process by creating a zip file of the
report directory on the fly while uploading it.

## Configuration
Configure the plugin in your project like this:
```xml
<project ..>
    <build>
        <plugins>
            <plugin>
                <groupId>org.scenarioo</groupId>
                <artifactId>scenarioo-upload-maven-plugin</artifactId>
                <version>1.0</version>
                <configuration>
                    <reportDirectory>${project.build.directory}/scenarioo-reports</reportDirectory>
                    <scenariooServerUrl>http://localhost:8080/scenarioo</scenariooServerUrl>
                    <user>scenarioo</user>
                    <password>secret</password>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The following parameters can be configured:

| parameter          | description |
|--------------------|-------------|
| createZipFile      | If set to `true` a zip file will be created on disk before the actual upload. If `false` the zip file will be created on the fly while uploading. Default: `false` |
| reportZipFile      | Absolute path of the zip file that will be created. Setting this parameter only makes sense when `createZipFile` is `true`. Default is `${project.build.directory}/scenarioo-report.zip` |
| reportDirectory    | Location of the report directory. **Required** |
| scenariooServerUrl | URL of the scenarioo viewer. Default is http://localhost:8080/scenarioo |
| user               | User for the upload. **Required** |
| password           | Password for the upload **Required** |
