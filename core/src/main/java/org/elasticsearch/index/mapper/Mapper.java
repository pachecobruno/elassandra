/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper;

import org.apache.cassandra.utils.ByteBufferUtil;
import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.similarity.SimilarityProvider;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Mapper implements ToXContent, Iterable<Mapper> {

    public static enum CqlCollection {
        LIST, SET, SINGLETON
    }
    
    public static enum CqlStruct {
        UDT, MAP, TUPLE
    }
    
    public static class BuilderContext {
        private final Settings indexSettings;
        private final ContentPath contentPath;

        public BuilderContext(Settings indexSettings, ContentPath contentPath) {
            this.contentPath = contentPath;
            this.indexSettings = indexSettings;
        }

        public ContentPath path() {
            return this.contentPath;
        }

        @Nullable
        public Settings indexSettings() {
            return this.indexSettings;
        }

        @Nullable
        public Version indexCreatedVersion() {
            if (indexSettings == null) {
                return null;
            }
            return Version.indexCreated(indexSettings);
        }
    }

    public abstract static class Builder<T extends Builder, Y extends Mapper> {

        public String name;

        protected T builder;

        protected Builder(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        /** Returns a newly built mapper. */
        public abstract Y build(BuilderContext context);
    }

    public interface TypeParser {

        class ParserContext {

            private final String type;

            private final IndexAnalyzers indexAnalyzers;

            private final Function<String, SimilarityProvider> similarityLookupService;

            private final MapperService mapperService;

            private final Function<String, TypeParser> typeParsers;

            private final Version indexVersionCreated;

            private final Supplier<QueryShardContext> queryShardContextSupplier;

            public ParserContext(String type, IndexAnalyzers indexAnalyzers, Function<String, SimilarityProvider> similarityLookupService,
                                 MapperService mapperService, Function<String, TypeParser> typeParsers,
                                 Version indexVersionCreated, Supplier<QueryShardContext> queryShardContextSupplier) {
                this.type = type;
                this.indexAnalyzers = indexAnalyzers;
                this.similarityLookupService = similarityLookupService;
                this.mapperService = mapperService;
                this.typeParsers = typeParsers;
                this.indexVersionCreated = indexVersionCreated;
                this.queryShardContextSupplier = queryShardContextSupplier;
            }

            public String type() {
                return type;
            }

            public IndexAnalyzers getIndexAnalyzers() {
                return indexAnalyzers;
            }

            public SimilarityProvider getSimilarity(String name) {
                return similarityLookupService.apply(name);
            }

            public MapperService mapperService() {
                return mapperService;
            }

            public TypeParser typeParser(String type) {
                return typeParsers.apply(type);
            }

            public Version indexVersionCreated() {
                return indexVersionCreated;
            }

            public Supplier<QueryShardContext> queryShardContextSupplier() {
                return queryShardContextSupplier;
            }

            public boolean isWithinMultiField() { return false; }

            protected Function<String, TypeParser> typeParsers() { return typeParsers; }

            protected Function<String, SimilarityProvider> similarityLookupService() { return similarityLookupService; }

            public ParserContext createMultiFieldContext(ParserContext in) {
                return new MultiFieldParserContext(in) {
                    @Override
                    public boolean isWithinMultiField() { return true; }
                };
            }

            static class MultiFieldParserContext extends ParserContext {
                MultiFieldParserContext(ParserContext in) {
                    super(in.type(), in.indexAnalyzers, in.similarityLookupService(), in.mapperService(), in.typeParsers(),
                            in.indexVersionCreated(), in.queryShardContextSupplier());
                }
            }

        }

        Mapper.Builder<?,?> parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException;
    }

    private final String simpleName;
    private ByteBuffer   cqlName;
    
    public Mapper(String simpleName) {
        Objects.requireNonNull(simpleName);
        this.simpleName = simpleName;
    }

    /** Returns the simple name, which identifies this mapper against other mappers at the same level in the mappers hierarchy
     * TODO: make this protected once Mapper and FieldMapper are merged together */
    public final String simpleName() {
        return simpleName;
    }

    /** Returns the canonical name which uniquely identifies the mapper against other mappers in a type. */
    public abstract String name();

    /** Return the merge of {@code mergeWith} into this.
     *  Both {@code this} and {@code mergeWith} will be left unmodified. */
    public abstract Mapper merge(Mapper mergeWith, boolean updateAllTypes);

    /**
     * Update the field type of this mapper. This is necessary because some mapping updates
     * can modify mappings across several types. This method must return a copy of the mapper
     * so that the current mapper is not modified.
     */
    public abstract Mapper updateFieldType(Map<String, MappedFieldType> fullNameToFieldType);
    

    /**
     * @return cql column name as a ByteBuffer
     */
    public ByteBuffer cqlName() {
        if (cqlName == null) {
            cqlName = ByteBufferUtil.bytes(this.simpleName);
        }
        return cqlName;
    }

    public abstract CqlCollection cqlCollection();
    
    public abstract String cqlCollectionTag();

    public abstract CqlStruct cqlStruct();
    
    public abstract boolean cqlPartialUpdate();
    
    public abstract boolean cqlPartitionKey();
    
    public abstract boolean cqlStaticColumn();
    
    public abstract int cqlPrimaryKeyOrder();
    
    public abstract boolean hasField();
    
    public String cqlType() {
        return null;
    }
}
