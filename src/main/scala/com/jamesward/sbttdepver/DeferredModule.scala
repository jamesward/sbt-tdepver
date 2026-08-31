package com.jamesward.sbttdepver

import sbt.internal.util.MessageOnlyException
import sbt.librarymanagement.*

private object DeferredModule:
  val MarkerRevision = "__sbt_tdepver_deferred__"

  private val AttributePrefix = "sbt-tdepver.source."
  private val OrganizationKey = AttributePrefix + "organization"
  private val NameKey = AttributePrefix + "name"
  private val CrossKindKey = AttributePrefix + "cross-kind"
  private val CrossPrefixKey = AttributePrefix + "cross-prefix"
  private val CrossSuffixKey = AttributePrefix + "cross-suffix"
  private val CrossValueKey = AttributePrefix + "cross-value"

  final case class Deferred(target: ModuleID, source: ModuleID)

  def encode(target: ModuleID, source: ModuleID): ModuleID =
    target.withExtraAttributes(target.extraAttributes ++ sourceAttributes(source))

  def decode(module: ModuleID): Option[Deferred] =
    Option.when(module.revision == MarkerRevision):
      val attributes = module.extraAttributes
      val organization = required(attributes, OrganizationKey)
      val name = required(attributes, NameKey)
      val crossVersion = decodeCrossVersion(attributes)
      val source = ModuleID(organization, name, MarkerRevision).withCrossVersion(crossVersion)
      Deferred(module, source)

  def concretize(module: ModuleID, revision: String): ModuleID =
    module
      .withRevision(revision)
      .withExtraAttributes(module.extraAttributes.filterNot(_._1.startsWith(AttributePrefix)))

  private def sourceAttributes(source: ModuleID): Map[String, String] =
    Map(
      OrganizationKey -> source.organization,
      NameKey -> source.name
    ) ++ encodeCrossVersion(source.crossVersion)

  private def encodeCrossVersion(crossVersion: CrossVersion): Map[String, String] =
    crossVersion match
      case _: Disabled => Map(CrossKindKey -> "disabled")
      case binary: Binary =>
        Map(
          CrossKindKey -> "binary",
          CrossPrefixKey -> binary.prefix,
          CrossSuffixKey -> binary.suffix
        )
      case full: Full =>
        Map(
          CrossKindKey -> "full",
          CrossPrefixKey -> full.prefix,
          CrossSuffixKey -> full.suffix
        )
      case constant: Constant =>
        Map(CrossKindKey -> "constant", CrossValueKey -> constant.value)
      case _: Patch => Map(CrossKindKey -> "patch")
      case compatible: For3Use2_13 =>
        Map(
          CrossKindKey -> "for3-use-2.13",
          CrossPrefixKey -> compatible.prefix,
          CrossSuffixKey -> compatible.suffix
        )
      case compatible: For2_13Use3 =>
        Map(
          CrossKindKey -> "for2.13-use-3",
          CrossPrefixKey -> compatible.prefix,
          CrossSuffixKey -> compatible.suffix
        )
      case unsupported =>
        throw new MessageOnlyException(
          s"sbt-tdepver does not support cross version $unsupported"
        )

  private def decodeCrossVersion(attributes: Map[String, String]): CrossVersion =
    required(attributes, CrossKindKey) match
      case "disabled" => Disabled()
      case "binary" => Binary(required(attributes, CrossPrefixKey), required(attributes, CrossSuffixKey))
      case "full" => Full(required(attributes, CrossPrefixKey), required(attributes, CrossSuffixKey))
      case "constant" => Constant(required(attributes, CrossValueKey))
      case "patch" => Patch()
      case "for3-use-2.13" =>
        For3Use2_13(required(attributes, CrossPrefixKey), required(attributes, CrossSuffixKey))
      case "for2.13-use-3" =>
        For2_13Use3(required(attributes, CrossPrefixKey), required(attributes, CrossSuffixKey))
      case kind =>
        throw new MessageOnlyException(s"sbt-tdepver found unknown cross version kind '$kind'")

  private def required(attributes: Map[String, String], key: String): String =
    attributes.getOrElse(
      key,
      throw new MessageOnlyException(s"sbt-tdepver dependency marker is missing '$key'")
    )
