package com.github.rougsig.mvifake.processor.viewgenerator

import com.github.rougsig.mvifake.processor.extensions.error
import com.github.rougsig.mvifake.runtime.FakeView
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.asTypeName
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.visibility
import me.eugeniomarletti.kotlin.processing.KotlinProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass

internal data class FakeViewType(
  val intents: List<IntentType>,
  val isInternal: Boolean,
  val viewElement: TypeElement,
  val viewName: String,
  val packageName: String
) {

  companion object {
    fun get(env: KotlinProcessingEnvironment, element: Element): FakeViewType? {
      val typeMetadata = element.kotlinMetadata
      if (element !is TypeElement || typeMetadata !is KotlinClassMetadata) {
        env.error("@FakeView can't be applied to $element: must be kotlin class", element)
        return null
      }

      val annotation = element.getAnnotationMirror(FakeView::class)
      val viewElement = ((env.typeUtils
        .asElement((annotation!!.getFieldByName("viewClass")!!.value as TypeMirror))
        .asType() as DeclaredType)
        .asElement() as TypeElement)

      val intents = viewElement.enclosedElements.mapNotNull { IntentType.get(env, it) }
      val isInternal = viewElement.getParents()
        .plus(viewElement)
        .filter { it.kotlinMetadata != null }
        .any { (it.kotlinMetadata as KotlinClassMetadata).data.classProto.visibility == ProtoBuf.Visibility.INTERNAL }

      return FakeViewType(
        intents,
        isInternal,
        viewElement,
        element.className.simpleName,
        element.className.packageName
      )
    }
  }
}

private val Element.className: ClassName
  get() {
    val typeName = asType().asTypeName()
    return when (typeName) {
      is ClassName -> typeName
      is ParameterizedTypeName -> typeName.rawType
      else -> throw IllegalStateException("unexpected TypeName: ${typeName::class}")
    }
  }

private fun TypeElement.getAnnotationMirror(annotationClass: KClass<*>): AnnotationMirror? {
  return annotationMirrors
    .find { it.annotationType.asElement().simpleName.toString() == annotationClass.simpleName.toString() }
}

private fun AnnotationMirror.getFieldByName(fieldName: String): AnnotationValue? {
  return elementValues.entries
    .firstOrNull { (element, _) ->
      element.simpleName.toString() == fieldName
    }
    ?.value
}

private fun Element.getParents(list: MutableList<Element> = mutableListOf()): List<Element> {
  if (enclosingElement == null) return list.reversed()
  return enclosingElement.getParents(list.apply { add(enclosingElement) })
}
