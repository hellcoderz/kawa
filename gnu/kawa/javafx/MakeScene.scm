(require gnu.kawa.javafx.GroupObjectBuilder)

(define-simple-class MakeScene ()
  (title ::java.lang.String)
  (builder access: 'private ::javafx.scene.SceneBuilder)
  (group access: 'private ::javafx.scene.Group)
  (root access: 'private ::javafx.scene.Parent)
  ((*init*)
   (set! builder (javafx.scene.SceneBuilder:create)))
  ((setTitle x::java.lang.String) (set! title x))
  ((setCamera x::javafx.scene.Camera)::void (builder:camera x))
  ((setDepthBuffer x::boolean)::void (builder:depthBuffer x))
  ((setCursor x::javafx.scene.Cursor)::void (builder:cursor x))
  ((setEventDispatcher x::javafx.event.EventDispatcher)::void (builder:eventDispatcher x))
  ((setFill x::javafx.scene.paint.Paint)::void (builder:fill x))
  ((setWidth x::double)::void (builder:width x))
  ((setHeight x::double)::void (builder:height x))
  ((setRoot r::javafx.scene.Parent)::void
   (if group
       (primitive-throw (java.lang.IllegalArgumentException "setting root after adding children")))
   (set! root r))
  ((getGroup)::javafx.scene.Group
   (cond ((not group)
          (if root
              (primitive-throw (java.lang.IllegalArgumentException "adding children after setting root")))
          (set! group (javafx.scene.Group))))
   group)
  ((add n::javafx.scene.Node)
   ((getGroup):children:add n))
  ((setNodes n::java.util.List)::void
   (let ((ch (getGroup):children))
     ;;(ch:addAll n))
     (let ((k (n:size)))
       (do ((i ::int 0 (+ i 1))) ((= i k))
         (ch:add (n:get i))))))
  ((build)::javafx.scene.Scene
   (builder:root (if root root (getGroup)))
   (builder:build)))