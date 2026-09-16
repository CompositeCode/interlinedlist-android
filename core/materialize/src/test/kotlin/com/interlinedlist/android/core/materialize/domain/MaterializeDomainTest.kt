package com.interlinedlist.android.core.materialize.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The domain type is the contract: the four destinations, the five source kinds,
 * and the combinations the API refuses.
 */
class MaterializeDomainTest {

    @Test
    fun `each destination carries its own target`() {
        val source = MaterializeSource.Document("doc_1")

        assertThat(MaterializeRequest.ToList(source, ListConfig("t")).target)
            .isEqualTo(MaterializeTarget.LIST)
        assertThat(MaterializeRequest.ToDocument(source).target)
            .isEqualTo(MaterializeTarget.DOC)
        assertThat(MaterializeRequest.ToListAndDocument(source, ListConfig("t")).target)
            .isEqualTo(MaterializeTarget.BOTH)
        assertThat(MaterializeRequest.ToMessageDraft(source).target)
            .isEqualTo(MaterializeTarget.MESSAGE)
    }

    @Test
    fun `only the message target creates nothing`() {
        assertThat(MaterializeTarget.entries.filterNot { it.createsContent })
            .containsExactly(MaterializeTarget.MESSAGE)
    }

    @Test
    fun `targets use the documented wire values`() {
        assertThat(MaterializeTarget.entries.map { it.apiValue })
            .containsExactly("list", "doc", "both", "message").inOrder()
    }

    @Test
    fun `source kinds use the documented discriminators`() {
        assertThat(MaterializeSource.Messages(listOf("m")).kind).isEqualTo("messages")
        assertThat(MaterializeSource.Lists(listOf("l")).kind).isEqualTo("lists")
        assertThat(MaterializeSource.Rows("l", listOf("r")).kind).isEqualTo("rows")
        assertThat(MaterializeSource.Document("d").kind).isEqualTo("document")
        assertThat(MaterializeSource.DocumentSelection("d", "# h").kind).isEqualTo("docElements")
    }

    @Test
    fun `an empty selection cannot be materialized`() {
        listOf<() -> Any>(
            { MaterializeSource.Messages(emptyList()) },
            { MaterializeSource.Lists(emptyList()) },
            { MaterializeSource.Rows("lst_1", emptyList()) },
            { MaterializeSource.Rows("", listOf("row_1")) },
            { MaterializeSource.Document(" ") },
            { MaterializeSource.DocumentSelection("doc_1", "  ") },
        ).forEach { build ->
            runCatching { build() }.also {
                assertThat(it.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            }
        }
    }

    @Test
    fun `a list cannot be created without a title - the server requires one`() {
        val thrown = runCatching { ListConfig(title = " ") }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `column types cover the twelve the schema accepts`() {
        assertThat(ListColumnType.entries.map { it.apiValue }).containsExactly(
            "text", "textarea", "number", "boolean", "date", "datetime",
            "email", "url", "tel", "select", "multiselect", "priority",
        )
        assertThat(ListColumnType.fromApiValue("multiselect"))
            .isEqualTo(ListColumnType.MULTISELECT)
        assertThat(ListColumnType.fromApiValue("integer")).isNull()
    }

    @Test
    fun `document styles use the documented spellings`() {
        assertThat(DocumentListStyle.entries.map { it.apiValue })
            .containsExactly("numbered", "bulleted")
        assertThat(RowDataStyle.entries.map { it.apiValue })
            .containsExactly("inline", "sub-items")
    }
}
