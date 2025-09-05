package com.creditx.main.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Value("${api.doc.show-internal:false}")
        private boolean showInternal;

    @Bean
        public OpenAPI mainServiceOpenAPI() {
                OpenAPI api = new OpenAPI()
                                .info(new Info()
                                                .title("CreditX Main Service API")
                                                .description("Primary credit transaction orchestration APIs")
                                                .version("v1")
                                                .contact(new Contact().name("CreditX Platform Team").email("platform@creditx.local"))
                                                .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                                .externalDocs(new ExternalDocumentation()
                                                .description("CreditX Platform Docs")
                                                .url("https://docs.creditx.local"));

                api.addTagsItem(new Tag().name("public").description("Publicly supported API (stable contract)"));
                api.addTagsItem(new Tag().name("internal").description("Internal backend API (subject to change / not for external consumers)"));
                return api;
    }

        @Bean
        public OpenApiCustomizer internalTagHidingCustomiser() {
                return openApi -> {
                        if (showInternal || openApi.getPaths() == null) return;
                        var removePaths = new java.util.ArrayList<String>();
                        openApi.getPaths().forEach((path, pathItem) -> {
                                boolean allRemoved = true;
                                for (PathItem.HttpMethod method : PathItem.HttpMethod.values()) {
                                        Operation op = pathItem.readOperationsMap().get(method);
                                        if (op == null) continue;
                                        if (isInternal(op)) {
                                                switch (method) {
                                                        case GET -> pathItem.setGet(null);
                                                        case POST -> pathItem.setPost(null);
                                                        case PUT -> pathItem.setPut(null);
                                                        case DELETE -> pathItem.setDelete(null);
                                                        case PATCH -> pathItem.setPatch(null);
                                                        case OPTIONS -> pathItem.setOptions(null);
                                                        case HEAD -> pathItem.setHead(null);
                                                        case TRACE -> pathItem.setTrace(null);
                                                }
                                        } else {
                                                allRemoved = false;
                                        }
                                }
                                if (allRemoved) removePaths.add(path);
                        });
                        removePaths.forEach(p -> openApi.getPaths().remove(p));
                };
        }

        private boolean isInternal(Operation operation) {
                return operation.getTags() != null && operation.getTags().stream().anyMatch(t -> "internal".equalsIgnoreCase(t));
        }
}
