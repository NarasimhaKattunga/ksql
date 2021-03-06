/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.parser;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.confluent.ksql.ddl.DdlConfig;
import io.confluent.ksql.function.TestFunctionRegistry;
import io.confluent.ksql.metastore.KsqlStream;
import io.confluent.ksql.metastore.KsqlTable;
import io.confluent.ksql.metastore.KsqlTopic;
import io.confluent.ksql.metastore.MutableMetaStore;
import io.confluent.ksql.parser.KsqlParser.PreparedStatement;
import io.confluent.ksql.parser.tree.AliasedRelation;
import io.confluent.ksql.parser.tree.ComparisonExpression;
import io.confluent.ksql.parser.tree.CreateStream;
import io.confluent.ksql.parser.tree.Join;
import io.confluent.ksql.parser.tree.JoinCriteria;
import io.confluent.ksql.parser.tree.JoinOn;
import io.confluent.ksql.parser.tree.NodeLocation;
import io.confluent.ksql.parser.tree.PrimitiveType;
import io.confluent.ksql.parser.tree.QualifiedName;
import io.confluent.ksql.parser.tree.Statement;
import io.confluent.ksql.parser.tree.StringLiteral;
import io.confluent.ksql.parser.tree.Table;
import io.confluent.ksql.parser.tree.TableElement;
import io.confluent.ksql.parser.tree.Type;
import io.confluent.ksql.parser.tree.WithinExpression;
import io.confluent.ksql.serde.json.KsqlJsonTopicSerDe;
import io.confluent.ksql.util.MetaStoreFixture;
import io.confluent.ksql.util.timestamp.MetadataTimestampExtractionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.Before;
import org.junit.Test;

public class SqlFormatterTest {

  private AliasedRelation leftAlias;
  private AliasedRelation rightAlias;
  private JoinCriteria criteria;
  private NodeLocation location;

  private MutableMetaStore metaStore;

  private static final Schema addressSchema = SchemaBuilder.struct()
      .field("NUMBER", Schema.OPTIONAL_INT64_SCHEMA)
      .field("STREET", Schema.OPTIONAL_STRING_SCHEMA)
      .field("CITY", Schema.OPTIONAL_STRING_SCHEMA)
      .field("STATE", Schema.OPTIONAL_STRING_SCHEMA)
      .field("ZIPCODE", Schema.OPTIONAL_INT64_SCHEMA)
      .optional().build();

  private static final Schema categorySchema = SchemaBuilder.struct()
      .field("ID", Schema.OPTIONAL_INT64_SCHEMA)
      .field("NAME", Schema.OPTIONAL_STRING_SCHEMA)
      .optional().build();

  private static final Schema itemInfoSchema = SchemaBuilder.struct()
      .field("ITEMID", Schema.INT64_SCHEMA)
      .field("NAME", Schema.STRING_SCHEMA)
      .field("CATEGORY", categorySchema)
      .optional().build();

  private static final SchemaBuilder schemaBuilder = SchemaBuilder.struct();
  private static final Schema schemaBuilderOrders = schemaBuilder
      .field("ORDERTIME", Schema.INT64_SCHEMA)
      .field("ORDERID", Schema.OPTIONAL_INT64_SCHEMA)
      .field("ITEMID", Schema.OPTIONAL_STRING_SCHEMA)
      .field("ITEMINFO", itemInfoSchema)
      .field("ORDERUNITS", Schema.INT32_SCHEMA)
      .field("ARRAYCOL",SchemaBuilder.array(Schema.FLOAT64_SCHEMA).optional().build())
      .field("MAPCOL", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.FLOAT64_SCHEMA).optional().build())
      .field("ADDRESS", addressSchema)
      .build();


  @Before
  public void setUp() {
    final Table left = new Table(QualifiedName.of(Collections.singletonList("left")));
    final Table right = new Table(QualifiedName.of(Collections.singletonList("right")));
    leftAlias = new AliasedRelation(left, "l", Collections.emptyList());
    rightAlias = new AliasedRelation(right, "r", Collections.emptyList());

    criteria = new JoinOn(new ComparisonExpression(ComparisonExpression.Type.EQUAL,
                                                   new StringLiteral("left.col0"),
                                                   new StringLiteral("right.col0")));
    location = new NodeLocation(0, 0);

    metaStore = MetaStoreFixture.getNewMetaStore(new TestFunctionRegistry());



    final KsqlTopic
        ksqlTopicOrders =
        new KsqlTopic("ADDRESS_TOPIC", "orders_topic", new KsqlJsonTopicSerDe(), false);

    final KsqlStream ksqlStreamOrders = new KsqlStream<>(
        "sqlexpression",
        "ADDRESS",
        schemaBuilderOrders,
        schemaBuilderOrders.field("ORDERTIME"),
        new MetadataTimestampExtractionPolicy(),
        ksqlTopicOrders,
        Serdes.String());

    metaStore.putTopic(ksqlTopicOrders);
    metaStore.putSource(ksqlStreamOrders);

    final KsqlTopic
        ksqlTopicItems =
        new KsqlTopic("ITEMS_TOPIC", "item_topic", new KsqlJsonTopicSerDe(), false);
    final KsqlTable<String> ksqlTableOrders = new KsqlTable<>(
        "sqlexpression",
        "ITEMID",
        itemInfoSchema,
        itemInfoSchema.field("ITEMID"),
        new MetadataTimestampExtractionPolicy(),
        ksqlTopicItems,
        "items",
        Serdes.String());
    metaStore.putTopic(ksqlTopicItems);
    metaStore.putSource(ksqlTableOrders);
  }

  @Test
  public void testFormatSql() {

    final ArrayList<TableElement> tableElements = new ArrayList<>();
    tableElements.add(new TableElement("GROUP", new PrimitiveType(Type.KsqlType.STRING)));
    tableElements.add(new TableElement("NOLIT", new PrimitiveType(Type.KsqlType.STRING)));
    tableElements.add(new TableElement("Having", new PrimitiveType(Type.KsqlType.STRING)));

    final CreateStream createStream = new CreateStream(
        QualifiedName.of("TEST"),
        tableElements,
        false,
        Collections.singletonMap(
            DdlConfig.TOPIC_NAME_PROPERTY,
            new StringLiteral("topic_test")
        ));
    final String sql = SqlFormatter.formatSql(createStream);
    assertThat("literal escaping failure", sql, containsString("`GROUP` STRING"));
    assertThat("not literal escaping failure", sql, containsString("NOLIT STRING"));
    assertThat("lowercase literal escaping failure", sql, containsString("`Having` STRING"));
    final List<PreparedStatement<?>> statements = KsqlParserTestUtil.buildAst(sql,
        MetaStoreFixture.getNewMetaStore(new TestFunctionRegistry()));
    assertFalse("formatted sql parsing error", statements.isEmpty());
  }

  @Test
  public void shouldFormatCreateWithEmptySchema() {
    final CreateStream createStream = new CreateStream(
        QualifiedName.of("TEST"),
        Collections.emptyList(),
        false,
        Collections.singletonMap(
            DdlConfig.KAFKA_TOPIC_NAME_PROPERTY,
            new StringLiteral("topic_test")
        ));
    final String sql = SqlFormatter.formatSql(createStream);
    final String expectedSql = "CREATE STREAM TEST  WITH (KAFKA_TOPIC='topic_test');";
    assertThat(sql, equalTo(expectedSql));
  }

  @Test
  public void shouldFormatLeftJoinWithWithin() {
    final Join join = new Join(location, Join.Type.LEFT, leftAlias, rightAlias,
                         Optional.of(criteria),
                         Optional.of(new WithinExpression(10, TimeUnit.SECONDS)));

    final String expected = "left L\nLEFT OUTER JOIN right R WITHIN 10 SECONDS ON "
                            + "(('left.col0' = 'right.col0'))";
    assertEquals(expected, SqlFormatter.formatSql(join));
  }

  @Test
  public void shouldFormatLeftJoinWithoutJoinWindow() {
    final Join join = new Join(location, Join.Type.LEFT, leftAlias, rightAlias,
                               Optional.of(criteria), Optional.empty());

    final String result = SqlFormatter.formatSql(join);
    final String expected = "left L\nLEFT OUTER JOIN right R ON (('left.col0' = 'right.col0'))";
    assertEquals(expected, result);
  }

  @Test
  public void shouldFormatInnerJoin() {
    final Join join = new Join(location, Join.Type.INNER, leftAlias, rightAlias,
                               Optional.of(criteria),
                               Optional.of(new WithinExpression(10, TimeUnit.SECONDS)));

    final String expected = "left L\nINNER JOIN right R WITHIN 10 SECONDS ON "
                            + "(('left.col0' = 'right.col0'))";
    assertEquals(expected, SqlFormatter.formatSql(join));
  }

  @Test
  public void shouldFormatInnerJoinWithoutJoinWindow() {
    final Join join = new Join(location, Join.Type.INNER, leftAlias, rightAlias,
                               Optional.of(criteria),
                               Optional.empty());

    final String expected = "left L\nINNER JOIN right R ON (('left.col0' = 'right.col0'))";
    assertEquals(expected, SqlFormatter.formatSql(join));
  }


  @Test
  public void shouldFormatOuterJoin() {
    final Join join = new Join(location, Join.Type.OUTER, leftAlias, rightAlias,
                               Optional.of(criteria),
                               Optional.of(new WithinExpression(10, TimeUnit.SECONDS)));

    final String expected = "left L\nFULL OUTER JOIN right R WITHIN 10 SECONDS ON"
                            + " (('left.col0' = 'right.col0'))";
    assertEquals(expected, SqlFormatter.formatSql(join));
  }


  @Test
  public void shouldFormatOuterJoinWithoutJoinWindow() {
    final Join join = new Join(location, Join.Type.OUTER, leftAlias, rightAlias,
                               Optional.of(criteria),
                               Optional.empty());

    final String expected = "left L\nFULL OUTER JOIN right R ON (('left.col0' = 'right.col0'))";
    assertEquals(expected, SqlFormatter.formatSql(join));
  }

  @Test
  public void shouldFormatSelectQueryCorrectly() {
    final String statementString =
        "CREATE STREAM S AS SELECT a.address->city FROM address a;";
    final Statement statement = KsqlParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(SqlFormatter.formatSql(statement), equalTo("CREATE STREAM S AS SELECT FETCH_FIELD_FROM_STRUCT(A.ADDRESS, 'CITY') \"ADDRESS__CITY\"\n"
        + "FROM ADDRESS A"));
  }

  @Test
  public void shouldFormatSelectStarCorrectly() {
    final String statementString = "CREATE STREAM S AS SELECT * FROM address;";
    final Statement statement = KsqlParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(SqlFormatter.formatSql(statement),
        equalTo("CREATE STREAM S AS SELECT *\n"
            + "FROM ADDRESS ADDRESS"));
  }

  @Test
  public void shouldFormatSelectStarCorrectlyWithOtherFields() {
    final String statementString = "CREATE STREAM S AS SELECT *, address AS city FROM address;";
    final Statement statement = KsqlParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(SqlFormatter.formatSql(statement),
        equalTo("CREATE STREAM S AS SELECT\n"
            + "  *\n"
            + ", ADDRESS.ADDRESS \"CITY\"\n"
            + "FROM ADDRESS ADDRESS"));
  }

  @Test
  public void shouldFormatSelectStarCorrectlyWithJoin() {
    final String statementString = "CREATE STREAM S AS SELECT address.*, itemid.* "
        + "FROM address INNER JOIN itemid ON address.address = itemid.address->address;";
    final Statement statement = KsqlParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(SqlFormatter.formatSql(statement),
        equalTo("CREATE STREAM S AS SELECT\n"
            + "  ADDRESS.*\n"
            + ", ITEMID.*\n"
            + "FROM ADDRESS ADDRESS\n"
            + "INNER JOIN ITEMID ITEMID ON ((ADDRESS.ADDRESS = ITEMID.ADDRESS->ADDRESS))"));
  }

  @Test
  public void shouldFormatSelectStarCorrectlyWithJoinOneSidedStar() {
    final String statementString = "CREATE STREAM S AS SELECT address.*, itemid.ordertime "
        + "FROM address INNER JOIN itemid ON address.address = itemid.address->address;";
    final Statement statement = KsqlParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(SqlFormatter.formatSql(statement),
        equalTo("CREATE STREAM S AS SELECT\n"
            + "  ADDRESS.*\n"
            + ", ITEMID.ORDERTIME \"ORDERTIME\"\n"
            + "FROM ADDRESS ADDRESS\n"
            + "INNER JOIN ITEMID ITEMID ON ((ADDRESS.ADDRESS = ITEMID.ADDRESS->ADDRESS))"));
  }

  @Test
  public void shouldFormatSelectCorrectlyWithDuplicateFields() {
    final String statementString = "CREATE STREAM S AS SELECT address AS one, address AS two FROM address;";
    final Statement statement = KsqlParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(SqlFormatter.formatSql(statement),
        equalTo("CREATE STREAM S AS SELECT\n"
            + "  ADDRESS.ADDRESS \"ONE\"\n"
            + ", ADDRESS.ADDRESS \"TWO\"\n"
            + "FROM ADDRESS ADDRESS"));
  }
}

