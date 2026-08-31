package com.jamesward.sbttdepver

import sbt.librarymanagement.*

private object DependencyGraph:
  final case class ModuleKey(organization: String, name: String)

  final case class Graph(
      modules: Map[ModuleKey, ModuleID],
      outgoing: Map[ModuleKey, Set[ModuleKey]]
  ):
    def module(key: ModuleKey): Option[ModuleID] = modules.get(key)

    def reachable(
        from: ModuleKey,
        target: ModuleKey,
        ignoredEdges: Set[(ModuleKey, ModuleKey)] = Set.empty,
        blocked: Set[ModuleKey] = Set.empty
    ): Boolean =
      def loop(pending: List[ModuleKey], visited: Set[ModuleKey]): Boolean =
        pending match
          case Nil => false
          case head :: _ if head == target => true
          case head :: tail if visited.contains(head) || blocked.contains(head) =>
            loop(tail, visited)
          case head :: tail =>
            val next = outgoing
              .getOrElse(head, Set.empty)
              .filterNot(next => ignoredEdges.contains(head -> next))
              .toList
            loop(next ++ tail, visited + head)

      if blocked.contains(from) || blocked.contains(target) then false
      else loop(List(from), Set.empty)

  def graph(configuration: ConfigurationReport): Graph =
    val selected = configuration.modules.filterNot(_.evicted)
    val modules = selected.map(module => key(module.module) -> module.module).toMap
    val outgoing = selected
      .flatMap(module => module.callers.map(caller => key(caller.caller) -> key(module.module)))
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.toSet)
      .toMap
    Graph(modules, outgoing)

  def key(module: ModuleID): ModuleKey =
    ModuleKey(module.organization, module.name)
