package com.inmobi.grill.cli.commands;

/*
 * #%L
 * Grill CLI
 * %%
 * Copyright (C) 2014 Inmobi
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.inmobi.grill.api.APIResult;
import com.inmobi.grill.api.metastore.*;
import com.inmobi.grill.client.GrillClient;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class GrillDatabaseCommands implements CommandMarker {

  private GrillClient client;


  public void setClient(GrillClient client) {
    this.client = client;
  }


  @CliCommand(value = "show databases",
      help = "displays list of all databases")
  public String showAllDatabases() {
    List<String> databases = client.getAllDatabases();
    if(databases != null) {
      return Joiner.on("\n").join(databases);
    } else {
      return "No Dabases found";
    }
  }

  @CliCommand(value = "use",
      help = "change to new database")
  public String switchDatabase(@CliOption(key = {"", "db"},
      mandatory = true,
      help = "Database to change to") String database) {
    boolean status = client.setDatabase(database);
    if (status) {
      return "Successfully switched to " + database;
    } else {
      return "Failed to switch to " + database;
    }
  }


  @CliCommand(value = "create database", help = "create a database with specified name")
  public String createDatabase(
      @CliOption(key = {"", "db"}, mandatory = true, help = "Database to create")
      String database,
      @CliOption(key = {"ignore"}, mandatory = false,
          unspecifiedDefaultValue = "false")
      boolean ignore) {
    APIResult result = client.createDatabase(database, ignore);
    if (result.getStatus() == APIResult.Status.SUCCEEDED) {
      return ("Create database " + database + " successful");
    } else {
      return result.getMessage();
    }
  }

  @CliCommand(value = "drop database", help = "drop a database with specified name")
  public String dropDatabase(
      @CliOption(key = {"", "db"}, mandatory = true, help = "Database to drop")
      String database) {
    APIResult result = client.dropDatabase(database);
    if (result.getStatus() == APIResult.Status.SUCCEEDED) {
      return ("drop database " + database + " successful");
    } else {
      return result.getMessage();
    }
  }






}
