;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.openal
  (:require [com.stuartsierra.component :as component]
            [penumbra.openal.core :refer :all]
            [schema.core :as s])
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.openal ALC ALC10 ALC11 ALContext ALDevice]
           [org.lwjgl.system MemoryUtil]
           [org.newdawn.slick.openal WaveData]
           [java.nio ByteBuffer IntBuffer FloatBuffer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare destroy initialize)
(s/defrecord OpenAL [context :- s/Int
                     context-handle :- s/Int
                     device :- s/Int
                     context-frequency :- s/Int
                     context-refresh :- s/Int
                     context-sync :- s/Bool
                     device-args :- s/Str]
  component/Lifecycle
  (start
   [this]
   (into this (initialize this)))
  (stop
   [this]
   (into this (destroy this))
   this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(comment)
(al-import- alListener al-listener)
(al-import- alSource source-array)
(al-import- alSourcef source-f)
(al-import- alSourcei source-i)
(al-import- alGenSources gen-sources)
(al-import- alGenBuffers gen-buffers)
(al-import- alBufferData buffer-data)
(al-import- alDeleteSources delete-sources)
(al-import- alDeleteBuffers delete-buffers)

(al-import alSourcePlay play)
(al-import alSourcePause pause)
(al-import alSourceStop stop)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Initialization and Destruction

(s/defn destroy
  [component :- OpenAL]
  (let [dtor (fn [key]
               (when-let [x (key component)]
                 (.destroy x)))]
    (dtor :context)
    (dtor :device)))

(s/defn initialize
  [component :- OpenAL]
  (let [device (ALDevice. MemoryUtil/NULL)
        frequency (:context-frequency component)
        refresh (:context-refresh component)
        sync (:context-sync component)
        attribs (BufferUtils/createIntBuffer 16)]
    (doto attribs
      (.put ALC10/ALC_FREQUENCY)
      (.put frequency)
      (.put ALC10/ALC_REFRESH)
      (.put refresh)
      (.put ALC10/ALC_SYNC)
      (.put sync)
      ;; Q: What do the next 2 lines do?
      (.put 0)
      (.flip))
    (let [context-handle (ALC10/alcCreateContext (.getPointer device) attribs)
          context (ALContext. device context-handle)]
      {:context context
       :context-handle context-handle
       :device device})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

;;; TODO: Get the rest of these translated into something
;;; both meaningful and useful

(defn listener [property & args]
  (al-listener (enum property) (FloatBuffer/wrap (float-array args))))

(defn source [source property & args]
  (cond
    (< 1 (count args)) (source-array source (enum property) (FloatBuffer/wrap (float-array args)))
    (integer? (first args)) (source-i source (enum property) (first args))
    :else (source-f source (enum property) (first args))))

(defn gen-source []
  (let [ary (int-array 1)]
    (gen-sources (IntBuffer/wrap ary))
    (first ary)))

(defn gen-buffer []
  (let [ary (int-array 1)]
    (gen-buffers (IntBuffer/wrap ary))
    (first ary)))

(defn bind-buffer [src buf]
  (source-i src :buffer buf))

(defn load-wav-file [path]
  (let [wav (WaveData/create (java.io.FileInputStream. path))
        buf (gen-buffer)
        src (gen-source)]
    (try
      (buffer-data buf (.format wav) (.data wav) (.samplerate wav))
      (source src :buffer buf)
      src
      (finally
        (.dispose wav)))))

(comment (play (load-wav-file "/path/to/wav/file")))

;;; Pieces that I think I'm done translating

(s/defn capabilities
  [device :- ALDevice]
  (let [caps (.getCapabilities device)
        alc-11 (.getOpenALC11 caps)
        base {:alc-10 (.getOpenALC10 caps)
              :alc-11 alc-11
              :ext-exf (.getALC_EXT_EFX caps)
              :default-device (ALC10/alcGetString 0 ALC10/ALC_DEFAULT_DEVICE_SPECIFIER)}]
    (let [extra-11 (if alc-11
                     (let [extra-caps (ALC/getStringList 0 ALC11/ALC_ALL_DEVICES_SPECIFIER)]
                       (reduce (fn [acc cap]
                                 (assoc acc cap true))
                               base extra-caps))
                     base)]
      extra-11)))

(defn ctor
  [{:keys [device-args context-frequency context-refresh context-sync]
    :or {context-sync true}}]
  (map->OpenAL {:device-args device-args
                :context-frequency context-frequency
                :context-refresh context-refresh
                :context-sync context-sync}))
