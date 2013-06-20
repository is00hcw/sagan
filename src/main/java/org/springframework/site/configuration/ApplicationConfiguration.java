/*
 * Copyright 2012-2013 the original author or authors.
 *
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
 */
package org.springframework.site.configuration;

import java.util.Collections;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.bootstrap.SpringApplication;
import org.springframework.bootstrap.bind.RelaxedDataBinder;
import org.springframework.bootstrap.config.YamlPropertiesFactoryBean;
import org.springframework.bootstrap.context.annotation.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.HttpConfiguration;
import org.springframework.security.config.annotation.web.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.site.documentation.DocumentationService;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.social.connect.mem.InMemoryUsersConnectionRepository;
import org.springframework.social.connect.support.ConnectionFactoryRegistry;
import org.springframework.social.connect.web.ProviderSignInController;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.social.github.api.GitHub;
import org.springframework.social.github.api.impl.GitHubTemplate;
import org.springframework.social.github.connect.GitHubConnectionFactory;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;

@EnableAutoConfiguration
@Configuration
@ComponentScan(basePackages = "org.springframework.site")
public class ApplicationConfiguration {

	private static final Log logger = LogFactory
			.getLog(ApplicationConfiguration.class);

	@Autowired
	private DocumentationService documentationService;

	public static void main(String[] args) {
		build().run(args);
	}

	public static SpringApplication build() {
		SpringApplication application = new SpringApplication(
				ApplicationConfiguration.class);
		application.setDefaultCommandLineArgs(
				"--spring.template.mode=LEGACYHTML5",
				"--spring.template.cache=false");
		return application;
	}

	@Bean
	public GitHubConnectionFactory gitHubConnectionFactory() {
		return new GitHubConnectionFactory("fb06c006c2ed62fe9e8b",
				"164264e3f6d70c7c21713b7fa64225cb8d6107b2");
	}

	@Bean
	public GitHub gitHubTemplate() {
		// TODO parametrize auth token
		return new GitHubTemplate("5a0e089d267693b45926d7f620d85a2eb6a85da6");
	}

	@Configuration
	@Order(Integer.MAX_VALUE-1)
	protected static class SigninAuthenticationConfiguration extends
			WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpConfiguration http) throws Exception {
			http.antMatcher("/signin/github").authorizeUrls().anyRequest()
					.anonymous();
		}
	}

	@Configuration
	@Order(Integer.MAX_VALUE)
	protected static class AdminAuthenticationConfiguration extends
			WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpConfiguration http) throws Exception {
			http.exceptionHandling().authenticationEntryPoint(
					authenticationEntryPoint());
			http.antMatcher("/admin/**").authorizeUrls().anyRequest()
					.authenticated();
		}

		private AuthenticationEntryPoint authenticationEntryPoint() {
			// TODO: can we POST back here?
			return new LoginUrlAuthenticationEntryPoint("/signin");
		}

		@Bean
		public ProviderSignInController providerSignInController(
				GitHubConnectionFactory connectionFactory) {
			ConnectionFactoryRegistry registry = new ConnectionFactoryRegistry();
			registry.addConnectionFactory(connectionFactory);
			InMemoryUsersConnectionRepository repository = new InMemoryUsersConnectionRepository(
					registry);
			repository.setConnectionSignUp(new ConnectionSignUp() {
				@Override
				public String execute(Connection<?> connection) {
					return connection.getDisplayName() != null ? connection
							.getDisplayName() : null;
				}
			});
			return new ProviderSignInController(registry, repository,
					new SignInAdapter() {
						@Override
						public String signIn(String userId,
								Connection<?> connection,
								NativeWebRequest request) {
							// TODO: get group info from github and determine
							// role
							Authentication authentication = new UsernamePasswordAuthenticationToken(
									userId,
									"N/A",
									AuthorityUtils
											.commaSeparatedStringToAuthorityList("ROLE_USER"));
							SecurityContextHolder.getContext()
									.setAuthentication(authentication);
							return null;
						}
					});
		}
	}

	@PostConstruct
	public void loadDocumentationProjects() {
		bind("documentation.yml", documentationService);
	}

	public static void bind(String path,
			DocumentationService documentationService) {
		RelaxedDataBinder binder = new RelaxedDataBinder(documentationService);
		YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
		factory.setResources(new Resource[] { new ClassPathResource(path) });
		Properties properties = factory.getObject();
		logger.info("Binding properties: " + properties);
		properties.remove("projects");
		for (Object key : Collections.list(properties.propertyNames())) {
			if (key.toString().endsWith(("ersions"))) {
				properties.remove(key);
			}
		}
		binder.bind(new MutablePropertyValues(properties));
		Assert.state(!binder.getBindingResult().hasErrors(), "Errors binding "
				+ path + ": " + binder.getBindingResult().getAllErrors());
	}

}