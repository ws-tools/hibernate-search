/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.elasticsearch.test;

import static org.hibernate.search.elasticsearch.testutil.JsonHelper.assertJsonEquals;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.Id;

import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterFilterFactory;
import org.apache.lucene.analysis.standard.ClassicTokenizerFactory;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.AnalyzerDef;
import org.hibernate.search.annotations.AnalyzerDefs;
import org.hibernate.search.annotations.CharFilterDef;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Fields;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Parameter;
import org.hibernate.search.annotations.TokenFilterDef;
import org.hibernate.search.annotations.TokenizerDef;
import org.hibernate.search.elasticsearch.cfg.ElasticsearchEnvironment;
import org.hibernate.search.elasticsearch.cfg.IndexSchemaManagementStrategy;
import org.hibernate.search.elasticsearch.impl.ElasticsearchIndexManager;
import org.hibernate.search.elasticsearch.testutil.TestElasticsearchClient;
import org.hibernate.search.test.SearchInitializationTestBase;
import org.hibernate.search.test.util.ImmutableTestConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@link ElasticsearchIndexManager}'s schema creation feature.
 *
 * @author Yoann Rodiere
 */
@RunWith(Parameterized.class)
public class ElasticsearchSchemaCreationIT extends SearchInitializationTestBase {

	@Parameters(name = "With strategy {0}")
	public static EnumSet<IndexSchemaManagementStrategy> strategies() {
		return EnumSet.complementOf( EnumSet.of(
				// Those strategies don't create the schema, so we don't test those
				IndexSchemaManagementStrategy.NONE, IndexSchemaManagementStrategy.VALIDATE
				) );
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Rule
	public TestElasticsearchClient elasticSearchClient = new TestElasticsearchClient();

	private final IndexSchemaManagementStrategy strategy;

	public ElasticsearchSchemaCreationIT(IndexSchemaManagementStrategy strategy) {
		super();
		this.strategy = strategy;
	}

	@Override
	protected void init(Class<?>... annotatedClasses) {
		Map<String, Object> settings = new HashMap<>();
		settings.put(
				"hibernate.search.default." + ElasticsearchEnvironment.INDEX_SCHEMA_MANAGEMENT_STRATEGY,
				strategy.name()
		);
		init( new ImmutableTestConfiguration( settings, annotatedClasses ) );
	}

	@Test
	public void dateField() throws Exception {
		elasticSearchClient.index( SimpleDateEntity.class )
				.ensureDoesNotExist().registerForCleanup();

		init( SimpleDateEntity.class );

		assertJsonEquals(
				"{"
					+ "'dynamic': 'strict',"
					+ "'properties': {"
							+ "'id': {"
									+ "'type': 'string',"
									+ "'index': 'not_analyzed',"
									+ "'store': true"
							+ "},"
							+ "'myField': {"
									+ "'type': 'date',"
									+ "'format': 'strict_date_optional_time||epoch_millis'"
							+ "}"
					+ "}"
				+ "}",
				elasticSearchClient.mapping( SimpleDateEntity.class ).get()
				);
	}

	@Test
	public void booleanField() throws Exception {
		elasticSearchClient.index( SimpleBooleanEntity.class )
				.ensureDoesNotExist().registerForCleanup();

		init( SimpleBooleanEntity.class );

		assertJsonEquals(
				"{"
					+ "'dynamic': 'strict',"
					+ "'properties': {"
							+ "'id': {"
									+ "'type': 'string',"
									+ "'index': 'not_analyzed',"
									+ "'store': true"
							+ "},"
							+ "'myField': {"
									+ "'type': 'boolean'"
							+ "}"
					+ "}"
				+ "}",
				elasticSearchClient.mapping( SimpleBooleanEntity.class ).get()
				);
	}

	@Test
	public void analyzers() throws Exception {
		elasticSearchClient.index( SimpleAnalyzedEntity.class )
				.ensureDoesNotExist().registerForCleanup();

		init( SimpleAnalyzedEntity.class );

		assertJsonEquals(
				"{"
					+ "'analyzer': {"
							+ "'analyzerWithSimpleComponents': {"
									+ "'char_filter': ['html_strip'],"
									+ "'tokenizer': 'whitespace',"
									+ "'filter': ['lowercase']"
							+ "},"
							+ "'analyzerWithNamedSimpleComponents': {"
									+ "'char_filter': ['namedCharFilter'],"
									+ "'tokenizer': 'namedTokenizer',"
									+ "'filter': ['namedTokenFilter']"
							+ "},"
							+ "'analyzerWithComplexComponents': {"
									+ "'char_filter': ['analyzerWithComplexComponents_HTMLStripCharFilterFactory'],"
									+ "'tokenizer': 'classic',"
									+ "'filter': ['analyzerWithComplexComponents_WordDelimiterFilterFactory']"
							+ "},"
							+ "'analyzerWithNamedComplexComponents': {"
									+ "'char_filter': ['custom-html-stripper'],"
									+ "'tokenizer': 'custom-classic-tokenizer',"
									+ "'filter': ['custom-word-delimiter']"
							+ "}"
					+ "},"
					+ "'char_filter': {"
							+ "'namedCharFilter': {"
									+ "'type': 'html_strip'"
							+ "},"
							+ "'analyzerWithComplexComponents_HTMLStripCharFilterFactory': {"
									+ "'type': 'html_strip',"
									+ "'escaped_tags': ['br', 'p']"
							+ "},"
							+ "'custom-html-stripper': {"
									+ "'type': 'html_strip',"
									+ "'escaped_tags': ['br', 'p']"
							+ "}"
					+ "},"
					+ "'tokenizer': {"
							+ "'namedTokenizer': {"
									+ "'type': 'whitespace'"
							+ "},"
							+ "'custom-classic-tokenizer': {"
									+ "'type': 'classic'"
							+ "}"
					+ "},"
					+ "'filter': {"
							+ "'namedTokenFilter': {"
									+ "'type': 'lowercase'"
							+ "},"
							+ "'analyzerWithComplexComponents_WordDelimiterFilterFactory': {"
									+ "'type': 'word_delimiter',"
									+ "'generate_word_parts': '1',"
									+ "'generate_number_parts': '1',"
									+ "'catenate_words': '0',"
									+ "'catenate_numbers': '0',"
									+ "'catenate_all': '0',"
									+ "'split_on_case_change': '0',"
									+ "'split_on_numerics': '0',"
									+ "'preserve_original': '1'"
							+ "},"
							+ "'custom-word-delimiter': {"
									+ "'type': 'word_delimiter',"
									+ "'generate_word_parts': '1',"
									+ "'generate_number_parts': '1',"
									+ "'catenate_words': '0',"
									+ "'catenate_numbers': '0',"
									+ "'catenate_all': '0',"
									+ "'split_on_case_change': '0',"
									+ "'split_on_numerics': '0',"
									+ "'preserve_original': '1'"
							+ "}"
					+ "}"
				+ "}",
				elasticSearchClient.index( SimpleAnalyzedEntity.class ).settings( "index.analysis" ).get()
				);
		assertJsonEquals(
				"{"
					+ "'dynamic': 'strict',"
					+ "'properties': {"
							+ "'id': {"
									+ "'type': 'string',"
									+ "'index': 'not_analyzed',"
									+ "'store': true"
							+ "},"
							+ "'myField1': {"
									+ "'type': 'string',"
									+ "'analyzer': 'analyzerWithSimpleComponents'"
							+ "},"
							+ "'myField2': {"
									+ "'type': 'string',"
									+ "'analyzer': 'analyzerWithNamedSimpleComponents'"
							+ "},"
							+ "'myField3': {"
									+ "'type': 'string',"
									+ "'analyzer': 'analyzerWithComplexComponents'"
							+ "},"
							+ "'myField4': {"
									+ "'type': 'string',"
									+ "'analyzer': 'analyzerWithNamedComplexComponents'"
							+ "}"
					+ "}"
				+ "}",
				elasticSearchClient.mapping( SimpleAnalyzedEntity.class ).get()
				);
	}

	@Indexed
	@Entity
	private static class SimpleBooleanEntity {
		@DocumentId
		@Id
		Long id;

		@Field
		Boolean myField;
	}

	@Indexed
	@Entity
	private static class SimpleDateEntity {
		@DocumentId
		@Id
		Long id;

		@Field
		Date myField;
	}

	@Indexed
	@Entity
	@AnalyzerDefs({
			@AnalyzerDef(
					name = "analyzerWithSimpleComponents",
					charFilters = @CharFilterDef(factory = HTMLStripCharFilterFactory.class),
					tokenizer = @TokenizerDef(factory = WhitespaceTokenizerFactory.class),
					filters = @TokenFilterDef(factory = LowerCaseFilterFactory.class)
			),
			@AnalyzerDef(
					name = "analyzerWithNamedSimpleComponents",
					charFilters = @CharFilterDef(name = "namedCharFilter", factory = HTMLStripCharFilterFactory.class),
					tokenizer = @TokenizerDef(name = "namedTokenizer", factory = WhitespaceTokenizerFactory.class),
					filters = @TokenFilterDef(name = "namedTokenFilter", factory = LowerCaseFilterFactory.class)
			),
			@AnalyzerDef(
					name = "analyzerWithComplexComponents",
					charFilters = @CharFilterDef(
							factory = HTMLStripCharFilterFactory.class,
							params = {
									@Parameter(name = "escapedTags", value = "br p")
							}
					),
					tokenizer = @TokenizerDef(
							factory = ClassicTokenizerFactory.class
					),
					filters = @TokenFilterDef(
							factory = WordDelimiterFilterFactory.class,
							params = {
									@Parameter(name = "generateWordParts", value = "1"),
									@Parameter(name = "generateNumberParts", value = "1"),
									@Parameter(name = "catenateWords", value = "0"),
									@Parameter(name = "catenateNumbers", value = "0"),
									@Parameter(name = "catenateAll", value = "0"),
									@Parameter(name = "splitOnCaseChange", value = "0"),
									@Parameter(name = "splitOnNumerics", value = "0"),
									@Parameter(name = "preserveOriginal", value = "1")
							}
					)
			),
			@AnalyzerDef(
					name = "analyzerWithNamedComplexComponents",
					charFilters = @CharFilterDef(
							name = "custom-html-stripper",
							factory = HTMLStripCharFilterFactory.class,
							params = {
									@Parameter(name = "escapedTags", value = "br p")
							}
					),
					tokenizer = @TokenizerDef(
							name = "custom-classic-tokenizer",
							factory = ClassicTokenizerFactory.class
					),
					filters = @TokenFilterDef(
							name = "custom-word-delimiter",
							factory = WordDelimiterFilterFactory.class,
							params = {
									@Parameter(name = "generateWordParts", value = "1"),
									@Parameter(name = "generateNumberParts", value = "1"),
									@Parameter(name = "catenateWords", value = "0"),
									@Parameter(name = "catenateNumbers", value = "0"),
									@Parameter(name = "catenateAll", value = "0"),
									@Parameter(name = "splitOnCaseChange", value = "0"),
									@Parameter(name = "splitOnNumerics", value = "0"),
									@Parameter(name = "preserveOriginal", value = "1")
							}
					)
			)
	})
	private static class SimpleAnalyzedEntity {
		@DocumentId
		@Id
		Long id;

		@Fields({
				@Field(name = "myField1", analyzer = @Analyzer(definition = "analyzerWithSimpleComponents")),
				@Field(name = "myField2", analyzer = @Analyzer(definition = "analyzerWithNamedSimpleComponents")),
				@Field(name = "myField3", analyzer = @Analyzer(definition = "analyzerWithComplexComponents")),
				@Field(name = "myField4", analyzer = @Analyzer(definition = "analyzerWithNamedComplexComponents"))
		})
		String myField;
	}

}