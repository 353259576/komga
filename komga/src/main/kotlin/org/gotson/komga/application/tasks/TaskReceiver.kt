package org.gotson.komga.application.tasks

import mu.KotlinLogging
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.BookSearch
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.infrastructure.jms.QUEUE_TASKS
import org.gotson.komga.infrastructure.jms.QUEUE_TASKS_TYPE
import org.gotson.komga.infrastructure.jms.QUEUE_TYPE
import org.gotson.komga.infrastructure.jms.QUEUE_UNIQUE_ID
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TaskReceiver(
  private val jmsTemplate: JmsTemplate,
  private val libraryRepository: LibraryRepository,
  private val bookRepository: BookRepository
) {

  fun scanLibraries() {
    libraryRepository.findAll().forEach { scanLibrary(it.id) }
  }

  fun scanLibrary(libraryId: String) {
    submitTask(Task.ScanLibrary(libraryId))
  }

  fun analyzeUnknownAndOutdatedBooks(library: Library) {
    bookRepository.findAllId(
      BookSearch(
        libraryIds = listOf(library.id),
        mediaStatus = listOf(Media.Status.UNKNOWN, Media.Status.OUTDATED)
      )
    ).forEach {
      submitTask(Task.AnalyzeBook(it))
    }
  }

  fun analyzeBook(bookId: String) {
    submitTask(Task.AnalyzeBook(bookId))
  }

  fun analyzeBook(book: Book) {
    submitTask(Task.AnalyzeBook(book.id))
  }

  fun generateBookThumbnail(bookId: String) {
    submitTask(Task.GenerateBookThumbnail(bookId))
  }

  fun refreshBookMetadata(
    bookId: String,
    capabilities: List<BookMetadataPatchCapability> = BookMetadataPatchCapability.values().toList()
  ) {
    submitTask(Task.RefreshBookMetadata(bookId, capabilities))
  }

  fun refreshBookMetadata(
    book: Book,
    capabilities: List<BookMetadataPatchCapability> = BookMetadataPatchCapability.values().toList()
  ) {
    submitTask(Task.RefreshBookMetadata(book.id, capabilities))
  }

  fun refreshSeriesMetadata(seriesId: String) {
    submitTask(Task.RefreshSeriesMetadata(seriesId))
  }

  fun aggregateSeriesMetadata(seriesId: String) {
    submitTask(Task.AggregateSeriesMetadata(seriesId))
  }

  private fun submitTask(task: Task) {
    logger.info { "Sending task: $task" }
    jmsTemplate.convertAndSend(QUEUE_TASKS, task) {
      it.apply {
        setStringProperty(QUEUE_TYPE, QUEUE_TASKS_TYPE)
        setStringProperty(QUEUE_UNIQUE_ID, task.uniqueId())
      }
    }
  }
}
