/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.kontinuity.catapult.web.rest;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.kontinuity.catapult.service.github.GithubResource;

/**
 *
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@ApplicationPath("/api")
public class RestApplication extends Application
{
   @Inject
   private GithubResource githubResource;

   @Override
   public Set<Object> getSingletons()
   {
      HashSet<Object> singletons = new HashSet<>();
      singletons.add(githubResource);
      return singletons;
   }
}
