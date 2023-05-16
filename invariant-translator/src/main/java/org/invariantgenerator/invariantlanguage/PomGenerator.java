package org.invariantgenerator.invariantlanguage;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class PomGenerator {

    private static final String pomTemplate =
            """
             <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
             	<modelVersion>4.0.0</modelVersion>
             	<groupId>org.invariants</groupId>
             	<artifactId>invariants</artifactId>
             	<version>0.1</version>
             	<packaging>jar</packaging>
             	<name>Flink Invariants</name>
             	<properties>
             		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
             		<flink.version>1.15.0</flink.version>
             		<target.java.version>11</target.java.version>
             		<scala.binary.version>2.12</scala.binary.version>
             		<maven.compiler.source>${target.java.version}</maven.compiler.source>
             		<maven.compiler.target>${target.java.version}</maven.compiler.target>
             		<log4j.version>2.17.1</log4j.version>
             	</properties>
             	<repositories>
             		<repository>
             			<id>central</id>
             			<name>Maven Central</name>
             			<layout>default</layout>
             			<url>https://repo1.maven.org/maven2</url>
             			<snapshots>
             				<enabled>false</enabled>
             			</snapshots>
             		</repository>
             		<repository>
             			<id>apache.snapshots</id>
             			<name>Apache Development Snapshot Repository</name>
             			<url>https://repository.apache.org/content/repositories/snapshots/</url>
             			<releases>
             				<enabled>false</enabled>
             			</releases>
             			<snapshots>
             				<enabled>true</enabled>
             			</snapshots>
             		</repository>
             	</repositories>
             	<dependencies>
             		<!-- Apache Flink dependencies -->
             		<!-- These dependencies are provided, because they should not be packaged into the JAR file. -->
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-streaming-java</artifactId>
             			<version>${flink.version}</version>
             			<scope>provided</scope>
             		</dependency>
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-clients</artifactId>
             			<version>${flink.version}</version>
             			<scope>provided</scope>
             		</dependency>
             		<!-- Add connector dependencies here. They must be in the default scope (compile). -->
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-cep</artifactId>
             			<version>${flink.version}</version>
             		</dependency>
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-connector-base</artifactId>
             			<version>${flink.version}</version>
             		</dependency>
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-table-runtime</artifactId>
             			<version>${flink.version}</version>
             		</dependency>
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-table-planner-loader</artifactId>
             			<version>${flink.version}</version>
             		</dependency>
             		<dependency>
             			<groupId>com.fasterxml.jackson.dataformat</groupId>
             			<artifactId>jackson-dataformat-avro</artifactId>
             			<version>2.8.5</version>
             		</dependency>
             		<dependency>
             			<groupId>org.apache.flink</groupId>
             			<artifactId>flink-connector-kafka</artifactId>
             			<version>${flink.version}</version>
             		</dependency>
             	</dependencies>
             
             	<build>
             		<plugins>
             			<!-- Java Compiler -->
             			<plugin>
             				<groupId>org.apache.maven.plugins</groupId>
             				<artifactId>maven-compiler-plugin</artifactId>
             				<version>3.1</version>
             				<configuration>
             					<source>11</source>
             					<target>11</target>
             				</configuration>
             			</plugin>
             
             			<!-- We use the maven-shade plugin to create a fat jar that contains all necessary dependencies. -->
             			<!-- Change the value of <mainClass>...</mainClass> if your program entry point changes. -->
             			<plugin>
             				<groupId>org.apache.maven.plugins</groupId>
             				<artifactId>maven-shade-plugin</artifactId>
             				<version>3.1.1</version>
             				<executions>
             				%s
             
             				</executions>
             			</plugin>
             		</plugins>
             	</build>
             </project>
             """;

    public static void generatePomFile(String outputDir, List<String> invariantNames) throws IOException {
        // each invariant gets separate entry in pom.xml
        // such that its jar gets generated on `mvn package`
        var pomExecutionEntries = new StringBuilder();
        for (var invariantName : invariantNames) {
            var executionEntry = String.format("""
					<execution>
						<id>%s</id>
						<phase>package</phase>
						<goals>
							<goal>shade</goal>
						</goals>
						<configuration>
							<outputFile>%s.jar</outputFile>
							<createDependencyReducedPom>false</createDependencyReducedPom>
							<artifactSet>
								<excludes>
									<exclude>org.apache.flink:flink-shaded-force-shading</exclude>
									<exclude>com.google.code.findbugs:jsr305</exclude>
									<exclude>org.slf4j:*</exclude>
									<exclude>org.apache.logging.log4j:*</exclude>
								</excludes>
							</artifactSet>
							<filters>
								<filter>
									<!-- Do not copy the signatures in the META-INF folder.
									Otherwise, this might cause SecurityExceptions when using the JAR. -->
									<artifact>*:*</artifact>
									<excludes>
										<exclude>META-INF/*.SF</exclude>
										<exclude>META-INF/*.DSA</exclude>
										<exclude>META-INF/*.RSA</exclude>
									</excludes>
								</filter>
							</filters>
							<transformers>
								<transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
									<mainClass>org.invariants.%s</mainClass>
								</transformer>
							</transformers>
						</configuration>
					</execution>""", invariantName, invariantName, invariantName);
            pomExecutionEntries.append(executionEntry);
        }

        String pomFileContent = String.format(pomTemplate, pomExecutionEntries);

        // write to pom.xml
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputDir + "pom.xml"));
        writer.write(pomFileContent);
        writer.close();
    }
}
