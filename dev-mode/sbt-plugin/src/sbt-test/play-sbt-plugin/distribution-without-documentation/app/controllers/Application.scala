/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package controllers

import play.api._
import play.api.mvc._

import javax.inject.Inject

/**
 * i will fail since I check for a undefined class [[Documentation]]
 */
class Application @Inject()(action: DefaultActionBuilder) extends ControllerHelpers {

  def index = action {
    Ok
  }

}
