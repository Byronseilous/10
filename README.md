# ussd


# Introduction

To be honest I have always had issues with USSD applications. I wanted a way to make creating USSD applications manageable.

Usually, USSD applications move from one state to another. Several cases must be managed. 

1. what input and what type do we expect?
2. how do we handle empty cases?
3. can we go back? 
4. do we want to log the current state?
5. do we want to version our ussd state machine?



# Problem statement
this is borrowed from the [Africastalking June Technical Challenge](https://github.com/AfricasTalkingTalent/TechnicalChallengeJune2019), just the first part.
> User journey: person dials the USSD Code and gets prompted for a username and email address. 

# What is this state machine?

I wont try to make this complex but I will choose to use a normal map in a `ussd.edn` file.

there are two fixed states `:start` and `:finish`. These tell us where we are. We also have all the valid choices in their own set which are the expected user inputs, plus we have versioning. 

What is appealing about this is that this map can be changed by someone who doesn't know anything about programming. 

the layout is pretty simple

| current state | via | next state |
|-------| ------| ----- |
| :start | :init | :text |
| :ready | "1" | :query-email |
| :query-email | :empty | :query-email |
| :query-email | :not-empty | :query-username |
| :query-username | :empty | :query-username |
| :query-username | :not-empty | :finish |


```clojure
{:fsm
 {:start {:init {:text "Welcome to the USSD application.\nWe are registering users.\nPress 1 to continue or just cancel.",
                 :next-state :ready}}
  :ready {"1" {:next-state :query-email
               :text "Please enter your email address."}}
  :query-email {:empty {:next-state :query-email
                        :text "Sorry, the text is empty.\nPlease enter an email"}
                :not-empty {:next-state :query-username
                            :text "Please enter a username."}}
  :query-username {:empty {:next-state :query-username
                           :text "Sorry, the text is empty.\nPlease enter a username"}
                   :not-empty {:next-state :finish
                               :text "Thank you for participating in the registration."}}
  :finish :finish}
 :choices #{"1"}
 :version "0.0.1"}

```

# the callback

when our ussd application receives a callback from the Africastalking Service we get:

1. session-id
2. phone-number
3. network-code
4. service-code
5. text

# how the database looks
```
{"ATUid_7c6bad2c84927fcd17cf8331b5c3e497"
  {:phone-number +254XXXXXXX
   :network-code XXXXXX
   :service-code *384*XXXX#
   :text ""
   :current-state :start
   :current-state-response "Welcome to the USSD Application\n"
   :past-state [{:from :start :via :init :ready}]}
```

By storing all the past states like this we are able to find out where our users are having a problem or even create a situation that allows our users to go back if they had an error of some kind.

# overview 

does the session exist? we check the current-state then we use the ussd finite state machine to to get the next state and then respond with the text response. 

example:

```clojure
{:next-state :query-email
 :text "Please enter your email address."}
``` 

if the session does not exist? we just `associate` the new session-id on to the database.


